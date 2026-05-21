package com.werkflow.admin.service;

import com.werkflow.admin.dto.credential.CredentialTestResultResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CredentialTestClientTest {

    private RestTemplate restTemplate;
    private CredentialTestClient client;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        client = new CredentialTestClient(restTemplate, "http://engine:8081");
    }

    @Test
    @DisplayName("test returns the engine's outcome on a 200 response")
    void test_happyPath_returnsEngineOutcome() {
        when(restTemplate.postForEntity(
                eq("http://engine:8081/api/internal/credentials/test"),
                any(),
                eq(CredentialTestResultResponse.class)))
            .thenReturn(ResponseEntity.ok(new CredentialTestResultResponse(true, "OK")));

        CredentialTestResultResponse result = client.test("tenant-1", "smtp", "default");

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("OK");
    }

    @Test
    @DisplayName("test returns generic error on transport failure — message does not leak detail")
    void test_transportFailure_returnsGenericError() {
        when(restTemplate.postForEntity(
                any(String.class),
                any(),
                eq(CredentialTestResultResponse.class)))
            .thenThrow(new ResourceAccessException("connection refused"));

        CredentialTestResultResponse result = client.test("tenant-1", "smtp", "default");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Credential test failed");
        assertThat(result.message()).doesNotContain("connection refused");
    }

    @Test
    @DisplayName("test returns generic error on non-200 response without body")
    void test_unexpectedStatus_returnsGenericError() {
        when(restTemplate.postForEntity(
                any(String.class),
                any(),
                eq(CredentialTestResultResponse.class)))
            .thenReturn(ResponseEntity.status(HttpStatus.ACCEPTED).body(null));

        CredentialTestResultResponse result = client.test("tenant-1", "smtp", "default");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Credential test failed");
    }
}
