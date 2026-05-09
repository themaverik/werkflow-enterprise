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

    public static final String PSS_CAPABILITIES   = "pss.capabilities";
    public static final String PSS_CANDIDATE_GROUPS = "pss.candidateGroups";
    public static final String PSS_FEEL_EXPRESSIONS = "pss.feelExpressions";
    public static final String PSS_CATEGORIES      = "pss.categories";
    public static final String PSS_VISIBILITY_POLICY = "pss.visibilityPolicy";
    public static final String PSS_DEPARTMENTS     = "pss.departments";
    public static final String PSS_TAGS            = "pss.tags";
    public static final String PSS_LOCALE          = "pss.locale";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                PSS_CAPABILITIES,
                PSS_CANDIDATE_GROUPS,
                PSS_FEEL_EXPRESSIONS,
                PSS_CATEGORIES,
                PSS_VISIBILITY_POLICY,
                PSS_DEPARTMENTS,
                PSS_TAGS,
                PSS_LOCALE
        );
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(200));
        return manager;
    }
}
