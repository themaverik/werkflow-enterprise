package com.werkflow.engine.action.credential;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultVersionedKeyValueOperations;
import org.springframework.vault.support.Versioned;

import java.util.Map;
import java.util.Optional;

/**
 * Read-only wrapper around Spring Vault's {@link VaultVersionedKeyValueOperations}
 * for the engine.
 *
 * <p>Engine never writes to OpenBao — its token is scoped read-only via the
 * {@code werkflow-engine} policy. Mirrors admin's {@code VaultCredentialStore}
 * minus the write/delete operations.
 *
 * <p>Paths are KV-v2 mount-relative (e.g. {@code "tenants/acme/slack-bot-token/default"});
 * the Spring Vault SDK prepends {@code secret/data/} automatically.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VaultReader {

    private final VaultVersionedKeyValueOperations kvOperations;

    /**
     * Reads the latest version of the secret at {@code path}.
     * Returns {@link Optional#empty()} when the path does not exist or has been deleted.
     *
     * @throws VaultException on transport/auth failure (callers should wrap in
     *                        {@link CredentialResolutionException} with a typed message)
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
}
