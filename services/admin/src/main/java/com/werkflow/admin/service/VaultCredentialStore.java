package com.werkflow.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultVersionedKeyValueOperations;
import org.springframework.vault.support.Versioned;

import java.util.Map;
import java.util.Optional;

/**
 * Thin wrapper around Spring Vault's {@link VaultVersionedKeyValueOperations}
 * exposing exactly the operations admin-service needs for tenant credentials.
 *
 * <p>Indirection serves three purposes:
 * <ul>
 *   <li>Keeps the {@link TenantCredentialService} compile-time free of
 *       {@code org.springframework.vault.*} types.</li>
 *   <li>Centralises the never-log-the-payload guarantee (no values
 *       appear in logs produced by this class).</li>
 *   <li>Provides a stable seam for tests — services can mock
 *       {@code VaultCredentialStore} without needing a real Vault.</li>
 * </ul>
 *
 * <p>Paths handed to this class are KV-v2 mount-relative
 * (e.g. {@code "tenants/acme/slack-bot-token/default"}); the Spring Vault
 * SDK adds the {@code secret/data/} prefix automatically.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VaultCredentialStore {

    private final VaultVersionedKeyValueOperations kvOperations;

    /**
     * Writes a new version of the secret at {@code path}.
     * KV-v2 automatically preserves previous versions for rollback.
     */
    public void write(String path, Map<String, Object> values) {
        log.info("Vault write: path={} fields={}", path, values.keySet());
        try {
            kvOperations.put(path, values);
        } catch (VaultException ex) {
            log.error("Vault write failed: path={}", path, ex);
            throw ex;
        }
    }

    /**
     * Reads the latest version of the secret at {@code path}.
     * Returns {@link Optional#empty()} if the path does not exist or has been deleted.
     */
    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> read(String path) {
        try {
            Versioned<Map<String, Object>> versioned = kvOperations.get(path);
            if (versioned == null || !versioned.hasData()) {
                return Optional.empty();
            }
            return Optional.of((Map<String, Object>) versioned.getRequiredData());
        } catch (VaultException ex) {
            log.error("Vault read failed: path={}", path, ex);
            throw ex;
        }
    }

    /**
     * Soft-deletes the latest version of the secret at {@code path}.
     * KV-v2 retains previous versions; permanent removal requires {@code destroy}
     * which is intentionally not exposed here (data-loss prevention).
     */
    public void delete(String path) {
        log.info("Vault delete: path={}", path);
        try {
            kvOperations.delete(path);
        } catch (VaultException ex) {
            log.error("Vault delete failed: path={}", path, ex);
            throw ex;
        }
    }
}
