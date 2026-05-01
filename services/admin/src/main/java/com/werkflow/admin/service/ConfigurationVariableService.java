package com.werkflow.admin.service;

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

    private final ConfigurationVariableRepository repository;

    @Transactional(readOnly = true)
    public List<ConfigVarResponse> listByTenant(String tenantCode) {
        return repository.findByTenantCodeOrderByVarKey(tenantCode)
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
        if (repository.existsByTenantCodeAndVarKey(request.tenantCode(), request.varKey())) {
            throw new IllegalArgumentException(
                "Config var already exists: " + request.varKey() + " for tenant " + request.tenantCode()
                + " — use PUT to update");
        }
        return toResponse(repository.save(fromRequest(new ConfigurationVariable(), request)));
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
        return toResponse(repository.save(fromRequest(existing, request)));
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
