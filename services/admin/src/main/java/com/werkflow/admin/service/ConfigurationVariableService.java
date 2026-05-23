package com.werkflow.admin.service;

import com.werkflow.admin.designtime.platform.service.LocaleProjector;
import com.werkflow.admin.dto.ConfigVarRequest;
import com.werkflow.admin.dto.ConfigVarResponse;
import com.werkflow.admin.entity.ConfigurationVariable;
import com.werkflow.admin.repository.ConfigurationVariableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConfigurationVariableService {

    // Mirrors LocaleProjector.LOCALE_VAR_TYPE — keep in sync (same "LOCALE" slug).
    private static final String LOCALE_VAR_TYPE = "LOCALE";

    private final ConfigurationVariableRepository repository;
    private final LocaleProjector localeProjector;

    @Transactional(readOnly = true)
    public List<ConfigVarResponse> listByTenant(String tenantCode) {
        return repository.findByTenantCodeOrderByVarKey(tenantCode)
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ConfigVarResponse> listByTenantAndType(String tenantCode, String varType) {
        return repository.findByTenantCodeAndVarTypeOrderByVarKey(tenantCode, varType)
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    /** Returns key→value map for FEEL context injection (used by DmnConfigVariableInjector). */
    @Transactional(readOnly = true)
    public Map<String, String> getVarMap(String tenantCode) {
        return repository.findByTenantCodeOrderByVarKey(tenantCode)
            .stream()
            .collect(Collectors.toMap(ConfigurationVariable::getVarKey, ConfigurationVariable::getVarValue));
    }

    @Transactional
    public ConfigVarResponse create(ConfigVarRequest request) {
        ConfigurationVariable target = repository
                .findByTenantCodeAndVarKey(request.tenantCode(), request.varKey())
                .orElse(new ConfigurationVariable());
        ConfigVarResponse response = toResponse(repository.save(fromRequest(target, request)));
        evictLocaleCacheIfNeeded(request);
        return response;
    }

    @Transactional
    public ConfigVarResponse update(Long id, ConfigVarRequest request) {
        ConfigurationVariable existing = repository.findById(id)
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Config var not found: " + id));
        // MED-05: tenant_code must not change on update — prevents cross-tenant variable hijack
        if (!existing.getTenantCode().equals(request.tenantCode())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN,
                "Cannot change tenant ownership of a config var");
        }
        ConfigVarResponse response = toResponse(repository.save(fromRequest(existing, request)));
        evictLocaleCacheIfNeeded(request);
        return response;
    }

    /**
     * Evicts the PSS locale/capabilities cache when a LOCALE config var is written.
     * Without this, {@link LocaleProjector#project} keeps serving the stale cached
     * value and the portal appears to revert to the USD default after a save.
     *
     * <p>Eviction runs inside the enclosing transaction (after save, before commit).
     * A concurrent reader between eviction and commit could transiently re-cache the
     * old value; it self-corrects on the cache TTL or the next save. Acceptable for
     * this low-concurrency settings path over an AFTER_COMMIT synchronization callback.
     */
    private void evictLocaleCacheIfNeeded(ConfigVarRequest request) {
        if (LOCALE_VAR_TYPE.equals(request.varType())) {
            localeProjector.evict(request.tenantCode());
        }
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Config var not found: " + id);
        }
        repository.deleteById(id);
    }

    private ConfigurationVariable fromRequest(ConfigurationVariable v, ConfigVarRequest r) {
        v.setTenantCode(r.tenantCode());
        v.setVarKey(r.varKey());
        v.setVarValue(r.varValue());
        v.setVarType(r.varType() != null ? r.varType() : "STRING");
        v.setDescription(r.description());
        return v;
    }

    private ConfigVarResponse toResponse(ConfigurationVariable v) {
        return new ConfigVarResponse(
            v.getId(), v.getTenantCode(), v.getVarKey(), v.getVarValue(),
            v.getVarType(), v.getDescription(), v.getCreatedAt(), v.getUpdatedAt());
    }
}
