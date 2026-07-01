package com.werkflow.engine.action.credential;

import com.werkflow.engine.action.credential.dto.CredentialPathDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression guard for the {@code @Cacheable(unless = ...)} SpEL on
 * {@link CredentialMetadataClient#resolvePath}. Spring unwraps {@code Optional}
 * return values, so {@code #result} is the unwrapped {@link CredentialPathDto}
 * (null when empty) — calling {@code .isPresent()} on it threw
 * {@code SpelEvaluationException} whenever a credential actually resolved,
 * surfacing as a 500 on the credential-test path.
 *
 * <p>These run through a real caching proxy on purpose — a plain Mockito unit
 * test never triggers the SpEL and cannot catch the bug (which is why the
 * existing {@code CredentialRegistryTest}, mocking this client, missed it).
 */
@SpringJUnitConfig
class CredentialMetadataClientCacheTest {

    private static final String TENANT = "default";
    private static final String TYPE = "http-header-auth";

    @Configuration
    @EnableCaching
    static class TestConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("credentialPaths");
        }

        @Bean
        RestTemplate serviceRestTemplate() {
            return mock(RestTemplate.class);
        }

        @Bean
        CredentialMetadataClient credentialMetadataClient(RestTemplate serviceRestTemplate) {
            return new CredentialMetadataClient(serviceRestTemplate, "http://admin.test");
        }
    }

    @Autowired
    private CredentialMetadataClient client;

    @Autowired
    private RestTemplate serviceRestTemplate;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void resetState() {
        reset(serviceRestTemplate);
        cacheManager.getCache("credentialPaths").clear();
    }

    private CredentialPathDto dto(String label) {
        return new CredentialPathDto(TENANT, TYPE, label, "tenants/" + TENANT + "/" + TYPE + "/" + label);
    }

    @Test
    @DisplayName("resolvePath returns the DTO without a SpEL error when a credential resolves")
    void resolvePath_nonEmpty_returnsDtoWithoutSpelError() {
        CredentialPathDto expected = dto("erp-api-key");
        when(serviceRestTemplate.getForObject(anyString(), eq(CredentialPathDto.class), any(), any(), any()))
                .thenReturn(expected);

        assertThatCode(() ->
                assertThat(client.resolvePath(TENANT, TYPE, "erp-api-key")).contains(expected))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("present result is cached — a second lookup does not re-query admin")
    void resolvePath_present_isCached() {
        when(serviceRestTemplate.getForObject(anyString(), eq(CredentialPathDto.class), any(), any(), any()))
                .thenReturn(dto("erp-api-key"));

        client.resolvePath(TENANT, TYPE, "erp-api-key");
        client.resolvePath(TENANT, TYPE, "erp-api-key");

        verify(serviceRestTemplate, times(1))
                .getForObject(anyString(), eq(CredentialPathDto.class), any(), any(), any());
    }

    @Test
    @DisplayName("empty result is not cached — a second lookup re-queries admin")
    void resolvePath_empty_isNotCached() {
        when(serviceRestTemplate.getForObject(anyString(), eq(CredentialPathDto.class), any(), any(), any()))
                .thenReturn(null);

        assertThat(client.resolvePath(TENANT, TYPE, "missing")).isEmpty();
        assertThat(client.resolvePath(TENANT, TYPE, "missing")).isEmpty();

        verify(serviceRestTemplate, times(2))
                .getForObject(anyString(), eq(CredentialPathDto.class), any(), any(), any());
    }
}
