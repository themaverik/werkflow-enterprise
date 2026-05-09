package com.werkflow.admin.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine cache configuration for the Platform Semantics Service.
 * All PSS caches use a 5-minute TTL, keyed by tenantId.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    // PSS caches (5-minute TTL, keyed by tenantCode)
    public static final String PSS_CAPABILITIES      = "pss.capabilities";
    public static final String PSS_CANDIDATE_GROUPS  = "pss.candidateGroups";
    public static final String PSS_FEEL_EXPRESSIONS  = "pss.feelExpressions";
    public static final String PSS_CATEGORIES        = "pss.categories";
    public static final String PSS_VISIBILITY_POLICY = "pss.visibilityPolicy";
    public static final String PSS_DEPARTMENTS       = "pss.departments";
    public static final String PSS_TAGS              = "pss.tags";
    public static final String PSS_LOCALE            = "pss.locale";

    // DTDS caches (30-minute TTL, keyed by tenantId:connectorKey[:operationId:direction])
    public static final String DTDS_CONNECTOR_DEF = "dtds.connectorDef";
    public static final String DTDS_OPERATIONS    = "dtds.operations";
    public static final String DTDS_SCHEMA        = "dtds.schema";
    public static final String DTDS_FIELDS        = "dtds.fields";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        // PSS caches — 5 min TTL
        com.github.benmanes.caffeine.cache.Caffeine<Object, Object> pssCaffeine =
                Caffeine.newBuilder()
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .maximumSize(200);
        manager.registerCustomCache(PSS_CAPABILITIES,
                pssCaffeine.build());
        manager.registerCustomCache(PSS_CANDIDATE_GROUPS,
                Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(200).build());
        manager.registerCustomCache(PSS_FEEL_EXPRESSIONS,
                Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(200).build());
        manager.registerCustomCache(PSS_CATEGORIES,
                Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(200).build());
        manager.registerCustomCache(PSS_VISIBILITY_POLICY,
                Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(200).build());
        manager.registerCustomCache(PSS_DEPARTMENTS,
                Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(200).build());
        manager.registerCustomCache(PSS_TAGS,
                Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(200).build());
        manager.registerCustomCache(PSS_LOCALE,
                Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(200).build());

        // DTDS caches — 30 min TTL
        manager.registerCustomCache(DTDS_CONNECTOR_DEF,
                Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.MINUTES).maximumSize(500).build());
        manager.registerCustomCache(DTDS_OPERATIONS,
                Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.MINUTES).maximumSize(500).build());
        manager.registerCustomCache(DTDS_SCHEMA,
                Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.MINUTES).maximumSize(1000).build());
        manager.registerCustomCache(DTDS_FIELDS,
                Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.MINUTES).maximumSize(1000).build());

        return manager;
    }
}
