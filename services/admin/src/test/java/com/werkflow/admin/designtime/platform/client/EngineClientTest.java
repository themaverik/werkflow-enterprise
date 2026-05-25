package com.werkflow.admin.designtime.platform.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EngineClient#getTier1RoleMappings()} — focused on graceful
 * degradation. The engine role-mappings endpoint is {@code permitAll()} today; these
 * tests lock in that an auth rejection (config drift) degrades to an empty map rather
 * than propagating, mirroring engine-unreachable behaviour.
 */
class EngineClientTest {

    private RestTemplate restTemplate;
    private EngineClient engineClient;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        engineClient = new EngineClient(restTemplate);
    }

    @Test
    @DisplayName("getTier1RoleMappings maps role→groups entries from the engine response")
    void getTier1RoleMappings_mapsEntries() {
        Map<String, Object> body = Map.of("mappings", List.of(
                Map.of("role", "WORKFLOW_ADMIN", "groups", List.of("ADMINS", "OPS"))));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(body));

        Map<String, List<String>> result = engineClient.getTier1RoleMappings();

        assertThat(result).containsEntry("WORKFLOW_ADMIN", List.of("ADMINS", "OPS"));
    }

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = {"UNAUTHORIZED", "FORBIDDEN"})
    @DisplayName("getTier1RoleMappings degrades to empty map when the endpoint rejects auth (401/403)")
    void getTier1RoleMappings_authRejected_returnsEmpty(HttpStatus status) {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenThrow(HttpClientErrorException.create(
                        status, status.getReasonPhrase(), HttpHeaders.EMPTY, null, null));

        assertThat(engineClient.getTier1RoleMappings()).isEmpty();
    }

    @Test
    @DisplayName("getTier1RoleMappings degrades to empty map when the engine is unreachable")
    void getTier1RoleMappings_engineUnreachable_returnsEmpty() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenThrow(new RestClientException("connection refused"));

        assertThat(engineClient.getTier1RoleMappings()).isEmpty();
    }
}
