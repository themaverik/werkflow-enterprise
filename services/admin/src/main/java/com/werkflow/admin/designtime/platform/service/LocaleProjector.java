package com.werkflow.admin.designtime.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.werkflow.admin.config.CacheConfig;
import com.werkflow.admin.designtime.platform.dto.LocaleEntry;
import com.werkflow.admin.repository.ConfigurationVariableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projects the tenant locale configuration from the LOCALE ConfigurationVariable.
 * Returns a safe USD default when no LOCALE var is configured.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocaleProjector {

    private static final String LOCALE_VAR_KEY = "tenantLocale";
    private static final String LOCALE_VAR_TYPE = "LOCALE";

    private final ConfigurationVariableRepository configRepo;
    private final ObjectMapper objectMapper;

    /**
     * Returns the tenant's locale settings. Falls back to USD defaults if unconfigured or unparseable.
     */
    @Cacheable(value = CacheConfig.PSS_LOCALE, key = "#tenantCode")
    @Transactional(readOnly = true)
    public LocaleEntry project(String tenantCode) {
        return configRepo.findByTenantCodeAndVarKey(tenantCode, LOCALE_VAR_KEY)
                .filter(v -> LOCALE_VAR_TYPE.equals(v.getVarType()))
                .map(v -> parseLocaleEntry(v.getVarValue(), tenantCode))
                .orElse(LocaleEntry.defaultUsd());
    }

    /** Evicts the locale cache for the tenant — call after any LOCALE config var mutation. */
    @CacheEvict(value = {CacheConfig.PSS_LOCALE, CacheConfig.PSS_CAPABILITIES}, key = "#tenantCode")
    public void evict(String tenantCode) {}

    private LocaleEntry parseLocaleEntry(String json, String tenantCode) {
        try {
            return objectMapper.readValue(json, LocaleEntry.class);
        } catch (JsonProcessingException e) {
            // Corrupted LOCALE var — fall back to USD rather than hard-failing the designer
            log.warn("Unparseable LOCALE config var for tenant '{}': {}", tenantCode, e.getMessage());
            return LocaleEntry.defaultUsd();
        }
    }
}
