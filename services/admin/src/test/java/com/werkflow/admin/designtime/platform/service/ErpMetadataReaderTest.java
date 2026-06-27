package com.werkflow.admin.designtime.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.werkflow.admin.dto.connector.ConnectorTestResponse;
import com.werkflow.admin.service.ConnectorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ErpMetadataReaderTest {

    @Mock
    ConnectorService connectorService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ErpMetadataReader reader() {
        return new ErpMetadataReader(connectorService, objectMapper);
    }

    private static ConnectorTestResponse response(int status, String body) {
        return ConnectorTestResponse.builder()
                .statusCode(status)
                .body(body)
                .truncated(false)
                .durationMs(1L)
                .build();
    }

    @Test
    void readCollection_unwrapsSpringPageContent() {
        when(connectorService.callConnector(eq("acme"), eq("org-directory"), eq("/departments"), eq("GET"), any()))
                .thenReturn(response(200, "{\"content\":[{\"code\":\"ENG\"},{\"code\":\"FIN\"}],\"totalElements\":2}"));

        List<JsonNode> items = reader().readCollection("acme", "/departments");

        assertThat(items).hasSize(2);
        assertThat(items.get(0).path("code").asText()).isEqualTo("ENG");
    }

    @Test
    void readCollection_toleratesBareArray() {
        when(connectorService.callConnector(any(), any(), any(), any(), any()))
                .thenReturn(response(200, "[{\"code\":\"ENG\"}]"));

        assertThat(reader().readCollection("acme", "/departments")).hasSize(1);
    }

    @Test
    void readCollection_returnsEmptyOnNon2xx() {
        when(connectorService.callConnector(any(), any(), any(), any(), any()))
                .thenReturn(response(404, "Not Found"));

        assertThat(reader().readCollection("acme", "/departments")).isEmpty();
    }

    @Test
    void readCollection_returnsEmptyWhenConnectorUnregistered() {
        when(connectorService.callConnector(any(), any(), any(), any(), any()))
                .thenThrow(new NoSuchElementException("Connector not found: org-directory"));

        assertThat(reader().readCollection("acme", "/departments")).isEmpty();
    }

    @Test
    void readCollection_returnsEmptyWhenCredentialMissing() {
        when(connectorService.callConnector(any(), any(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "org-directory credential not found"));

        assertThat(reader().readCollection("acme", "/departments")).isEmpty();
    }

    @Test
    void readCollection_returnsEmptyOnBlankBody() {
        when(connectorService.callConnector(any(), any(), any(), any(), any()))
                .thenReturn(response(200, "  "));

        assertThat(reader().readCollection("acme", "/departments")).isEmpty();
    }

    @Test
    void readCollection_returnsEmptyOnUnparseableBody() {
        when(connectorService.callConnector(any(), any(), any(), any(), any()))
                .thenReturn(response(200, "not-json{"));

        assertThat(reader().readCollection("acme", "/departments")).isEmpty();
    }
}
