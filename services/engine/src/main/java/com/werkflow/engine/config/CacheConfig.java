package com.werkflow.engine.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for caching
 * Uses Caffeine cache with per-cache TTL configuration
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache("userProfiles",
            Caffeine.newBuilder().expireAfterWrite(60, TimeUnit.SECONDS).maximumSize(500).build());
        manager.registerCustomCache("tenantConfig",
            Caffeine.newBuilder().expireAfterWrite(300, TimeUnit.SECONDS).maximumSize(100).build());
        manager.registerCustomCache("roleMappings",
            Caffeine.newBuilder().expireAfterWrite(300, TimeUnit.SECONDS).maximumSize(100).build());
        manager.registerCustomCache("configVars",
            Caffeine.newBuilder().expireAfterWrite(300, TimeUnit.SECONDS).maximumSize(100).build());
        Caffeine<Object, Object> defaultSpec = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS).maximumSize(100).recordStats();
        for (String name : List.of("serviceUrls", "processDefinitionNames", "formSchemas", "doaThresholds")) {
            manager.registerCustomCache(name, defaultSpec.build());
        }
        return manager;
    }
}
