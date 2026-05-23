package com.werkflow.admin.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatasourceEvictClientTest {

    private RestTemplate restTemplate;
    private DatasourceEvictClient client;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        client = new DatasourceEvictClient(restTemplate, "http://engine:8081");
    }

    @Test
    @DisplayName("evict posts to the engine evict endpoint with tenantId + ref")
    void evict_happyPath_postsToEngine() {
        when(restTemplate.postForEntity(
                eq("http://engine:8081/api/internal/datasources/evict"),
                any(),
                eq(Void.class)))
            .thenReturn(ResponseEntity.noContent().build());

        client.evict("tenant-1", "demo-h2-hris");

        verify(restTemplate, times(1)).postForEntity(
            eq("http://engine:8081/api/internal/datasources/evict"),
            any(),
            eq(Void.class));
    }

    @Test
    @DisplayName("evict swallows transport failures — best-effort, never throws")
    void evict_transportFailure_doesNotThrow() {
        when(restTemplate.postForEntity(any(String.class), any(), eq(Void.class)))
            .thenThrow(new ResourceAccessException("connection refused"));

        assertThatCode(() -> client.evict("tenant-1", "demo-h2-hris"))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("evict swallows non-2xx responses — best-effort, never throws")
    void evict_nonSuccess_doesNotThrow() {
        when(restTemplate.postForEntity(any(String.class), any(), eq(Void.class)))
            .thenReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());

        assertThatCode(() -> client.evict("tenant-1", "demo-h2-hris"))
            .doesNotThrowAnyException();
    }
}
