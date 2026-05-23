package com.werkflow.admin.service;

import com.werkflow.admin.dto.credential.CreateTenantCredentialRequest;
import com.werkflow.admin.dto.credential.CredentialPathResponse;
import com.werkflow.admin.dto.credential.CredentialTestResultResponse;
import com.werkflow.admin.dto.credential.TenantCredentialResponse;
import com.werkflow.admin.dto.credential.UpdateTenantCredentialRequest;
import com.werkflow.admin.entity.TenantCredential;
import com.werkflow.admin.entity.TenantDatasource;
import com.werkflow.admin.repository.TenantCredentialRepository;
import com.werkflow.admin.event.DatasourcePoolEvictionEvent;
import com.werkflow.admin.repository.TenantDatasourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.vault.VaultException;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates tenant credential storage between OpenBao (source of truth for
 * values) and the {@code tenant_credentials} metadata index in the admin DB.
 *
 * <p>Compensation strategy (per Phase B.2 brainstorm):
 * <ul>
 *   <li><b>Create</b>: Vault write first, then DB insert. On DB failure the Vault
 *       entry is deleted so no orphan remains.</li>
 *   <li><b>Update</b>: Vault write first (new kv-v2 version), then DB row
 *       {@code rotated_at} update. On DB failure we keep the new Vault version —
 *       reads still work; the metadata simply lags the rotation timestamp.</li>
 *   <li><b>Delete</b>: DB delete first, then Vault soft-delete. On Vault failure
 *       the metadata row is gone so the orphaned Vault data is unreachable through
 *       normal lookup; a warning is logged.</li>
 * </ul>
 *
 * <p>Test-connection support is intentionally not in this class — engine owns the
 * {@code CredentialType.validate} contract. See B.2 step 5 for the cross-service
 * test-connection wiring.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantCredentialService {

    private final TenantCredentialRepository repository;
    private final VaultCredentialStore vault;
    private final CredentialTestClient credentialTestClient;
    private final TenantDatasourceRepository datasourceRepository;
    private final ApplicationEventPublisher eventPublisher;

    // -- queries -------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<TenantCredentialResponse> list(String tenantId) {
        return repository.findByTenantId(tenantId).stream()
            .map(entity -> TenantCredentialResponse.from(entity, List.of()))
            .toList();
    }

    @Transactional(readOnly = true)
    public TenantCredentialResponse get(String tenantId, UUID id) {
        TenantCredential entity = findOwned(tenantId, id);
        return TenantCredentialResponse.from(entity, List.of());
    }

    /**
     * Engine-internal lookup. Returns the Vault path for a
     * {@code (tenantId, credentialType, label)} triple; engine reads Vault directly.
     *
     * @throws ResponseStatusException 404 if no metadata row exists
     */
    @Transactional(readOnly = true)
    public CredentialPathResponse resolvePath(String tenantId, String credentialType, String label) {
        TenantCredential entity = repository
            .findByTenantIdAndCredentialTypeAndLabel(tenantId, credentialType, label)
            .orElseThrow(() -> {
                log.debug("resolvePath: no row for tenant={} type={} label={}",
                    tenantId, credentialType, label);
                return new ResponseStatusException(HttpStatus.NOT_FOUND, "Credential not found");
            });
        return new CredentialPathResponse(
            entity.getTenantId(),
            entity.getCredentialType(),
            entity.getLabel(),
            entity.getVaultPath()
        );
    }

    // -- mutations -----------------------------------------------------------

    /**
     * Creates a new credential record: writes the value payload to Vault,
     * then inserts the metadata row. Vault write is rolled back on DB failure.
     *
     * @throws ResponseStatusException 409 if {@code (tenantId, type, label)} is already taken
     */
    public TenantCredentialResponse create(String tenantId, CreateTenantCredentialRequest req) {
        if (repository.existsByTenantIdAndCredentialTypeAndLabel(
                tenantId, req.credentialType(), req.label())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Credential already exists for type=" + req.credentialType()
                    + " label=" + req.label());
        }

        String vaultPath = buildVaultPath(tenantId, req.credentialType(), req.label());
        Map<String, Object> values = req.values();
        List<String> fieldNames = new ArrayList<>(values.keySet());

        // 1) Vault write first
        vault.write(vaultPath, values);

        // 2) DB insert; compensate on failure
        try {
            TenantCredential entity = new TenantCredential(
                tenantId, req.credentialType(), req.label(), vaultPath);
            TenantCredential saved = repository.save(entity);
            return TenantCredentialResponse.from(saved, fieldNames);
        } catch (RuntimeException dbEx) {
            log.error("DB insert failed after Vault write; compensating delete on path={}", vaultPath, dbEx);
            try {
                vault.delete(vaultPath);
            } catch (VaultException compensateEx) {
                log.error("Compensation delete failed; manual cleanup required for path={}", vaultPath, compensateEx);
            }
            throw dbEx;
        }
    }

    /**
     * Rotates a credential's values. The {@code credentialType} and {@code label}
     * are immutable; only the value payload changes.
     *
     * @throws ResponseStatusException 404 if the credential is missing or owned by another tenant
     */
    @Transactional
    public TenantCredentialResponse update(String tenantId, UUID id, UpdateTenantCredentialRequest req) {
        TenantCredential entity = findOwned(tenantId, id);
        Map<String, Object> values = req.values();
        List<String> fieldNames = new ArrayList<>(values.keySet());

        // 1) Vault write — new kv-v2 version
        vault.write(entity.getVaultPath(), values);

        // 2) Stamp rotation timestamp on DB row
        entity.setRotatedAt(OffsetDateTime.now());
        TenantCredential saved = repository.save(entity);

        // 3) Evict any engine pool using this credential after commit, so live pools
        // pick up the rotated secret (B.5 D7). Only jdbc-password credentials back a
        // datasource pool; AFTER_COMMIT-scoped listener fires the eviction.
        if ("jdbc-password".equals(entity.getCredentialType())) {
            datasourceRepository
                .findByTenantIdAndCredentialRef(tenantId, entity.getLabel())
                .forEach(ds -> eventPublisher.publishEvent(
                    new DatasourcePoolEvictionEvent(tenantId, ds.getRef())));
        }
        return TenantCredentialResponse.from(saved, fieldNames);
    }

    /**
     * Verifies a credential by delegating to engine's
     * {@code POST /api/internal/credentials/test} endpoint.
     *
     * <p>The tenant guard surfaces cross-tenant access as 404 (OWASP API BOLA).
     * Plaintext values never leave OpenBao — only the engine reads them and
     * returns a boolean+message outcome.
     *
     * @throws ResponseStatusException 404 if the credential is missing or owned by another tenant
     */
    @Transactional(readOnly = true)
    public CredentialTestResultResponse testConnection(String tenantId, UUID id) {
        TenantCredential entity = findOwned(tenantId, id);
        log.debug("testConnection: delegating to engine for tenant={} type={} label={}",
            tenantId, entity.getCredentialType(), entity.getLabel());
        return credentialTestClient.test(tenantId, entity.getCredentialType(), entity.getLabel());
    }

    /**
     * Removes a credential. DB row is deleted first; Vault soft-delete follows.
     * A Vault failure does not roll back the DB delete — the metadata row is
     * gone, so the orphaned Vault data is unreachable.
     *
     * <p>For {@code jdbc-password} credentials, deletion is blocked (409) when any
     * {@code tenant_datasource} row still references the credential by label, preventing
     * dangling {@code credentialRef} pointers.
     *
     * @throws ResponseStatusException 404 if the credential is missing or owned by another tenant
     * @throws ResponseStatusException 409 if a jdbc-password credential is referenced by a datasource
     */
    @Transactional
    public void delete(String tenantId, UUID id) {
        TenantCredential entity = findOwned(tenantId, id);

        if ("jdbc-password".equals(entity.getCredentialType())) {
            List<String> dependents = datasourceRepository
                .findByTenantIdAndCredentialRef(tenantId, entity.getLabel())
                .stream().map(TenantDatasource::getRef).toList();
            if (!dependents.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Credential is in use by datasource(s): " + String.join(", ", dependents));
            }
        }

        String vaultPath = entity.getVaultPath();
        repository.delete(entity);
        try {
            vault.delete(vaultPath);
        } catch (VaultException vaultEx) {
            log.warn("Vault soft-delete failed after DB delete; orphan at path={}", vaultPath, vaultEx);
        }
    }

    // -- helpers -------------------------------------------------------------

    /**
     * Loads an entity by id and asserts the caller's tenant owns it.
     * Cross-tenant access surfaces as 404 (do not leak existence).
     */
    private TenantCredential findOwned(String tenantId, UUID id) {
        TenantCredential entity = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Credential not found"));
        if (!entity.getTenantId().equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Credential not found");
        }
        return entity;
    }

    /**
     * Builds the KV-v2 mount-relative path for a credential.
     * Input segments are pre-validated by the entity/DTO {@code @Pattern} regex.
     */
    private String buildVaultPath(String tenantId, String credentialType, String label) {
        return "tenants/" + tenantId + "/" + credentialType + "/" + label;
    }
}
