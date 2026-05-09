package com.werkflow.admin.designtime.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.werkflow.admin.designtime.connector.entity.ConnectorDefinitionV2;
import com.werkflow.admin.designtime.connector.repository.ConnectorDefinitionV2Repository;
import com.werkflow.admin.designtime.connector.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectorCatalogServiceTest {

    @Mock
    ConnectorDefinitionV2Repository repo;
    @Mock
    ConnectorDefinitionValidator validator;
    @Spy
    ObjectMapper objectMapper = new ObjectMapper();
    @Spy
    SchemaResolverService schemaResolver = new SchemaResolverService(new ObjectMapper());
    @Spy
    SchemaFlattenerService schemaFlattener = new SchemaFlattenerService();

    @InjectMocks
    ConnectorCatalogService catalogService;

    private static final String TENANT = "acme";
    private static final String KEY = "test-connector";
    private static final String VERSION = "1.0.0";

    // -------------------------------------------------------------------------
    // register — happy path
    // -------------------------------------------------------------------------

    @Test
    void register_validDefinition_savesEntity() {
        String json = minimalConnectorJson(KEY, VERSION);
        when(repo.existsByKeyAndVersionAndTenantId(KEY, VERSION, TENANT)).thenReturn(false);
        when(repo.save(any())).thenAnswer(inv -> {
            ConnectorDefinitionV2 e = inv.getArgument(0);
            e.setCreatedAt(LocalDateTime.now());
            e.setUpdatedAt(LocalDateTime.now());
            return e;
        });
        doNothing().when(validator).validate(json);

        ConnectorDefinitionV2 result = catalogService.register(TENANT, json);

        assertThat(result.getKey()).isEqualTo(KEY);
        assertThat(result.getVersion()).isEqualTo(VERSION);
        assertThat(result.getTenantId()).isEqualTo(TENANT);
        verify(repo).save(any());
    }

    // -------------------------------------------------------------------------
    // register — duplicate
    // -------------------------------------------------------------------------

    @Test
    void register_duplicate_throwsConflict() {
        String json = minimalConnectorJson(KEY, VERSION);
        doNothing().when(validator).validate(json);
        when(repo.existsByKeyAndVersionAndTenantId(KEY, VERSION, TENANT)).thenReturn(true);

        assertThatThrownBy(() -> catalogService.register(TENANT, json))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(repo, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // register — validation failure
    // -------------------------------------------------------------------------

    @Test
    void register_invalidDefinition_throwsValidationException() {
        String badJson = "{}";
        doThrow(new ConnectorDefinitionValidator.ConnectorValidationException("invalid"))
                .when(validator).validate(badJson);

        assertThatThrownBy(() -> catalogService.register(TENANT, badJson))
                .isInstanceOf(ConnectorDefinitionValidator.ConnectorValidationException.class);

        verify(repo, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // getDefinitionJson — not found
    // -------------------------------------------------------------------------

    @Test
    void getDefinitionJson_connectorNotFound_throws404() {
        when(repo.findFirstByKeyAndTenantIdOrderByCreatedAtDesc(KEY, TENANT))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogService.getDefinitionJson(TENANT, KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // -------------------------------------------------------------------------
    // getDefinitionJson — secrets redacted
    // -------------------------------------------------------------------------

    @Test
    void getDefinitionJson_secretValueIsRedacted() {
        String jsonWithSecret = minimalConnectorJson(KEY, VERSION)
                .replace("}", ", \"secretValue\": \"my-actual-secret\"}");
        ConnectorDefinitionV2 entity = entity(KEY, VERSION, jsonWithSecret);
        when(repo.findFirstByKeyAndTenantIdOrderByCreatedAtDesc(KEY, TENANT))
                .thenReturn(Optional.of(entity));

        String result = catalogService.getDefinitionJson(TENANT, KEY);

        assertThat(result).doesNotContain("my-actual-secret");
        assertThat(result).contains("***");
    }

    // -------------------------------------------------------------------------
    // listOperations — parses correctly
    // -------------------------------------------------------------------------

    @Test
    void listOperations_returnsOperationSummaries() {
        ConnectorDefinitionV2 entity = entity(KEY, VERSION, minimalConnectorJson(KEY, VERSION));
        when(repo.findFirstByKeyAndTenantIdOrderByCreatedAtDesc(KEY, TENANT))
                .thenReturn(Optional.of(entity));

        var ops = catalogService.listOperations(TENANT, KEY);

        assertThat(ops).hasSize(1);
        assertThat(ops.get(0).id()).isEqualTo("defaultOp");
        assertThat(ops.get(0).displayName()).isEqualTo("Default Operation");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String minimalConnectorJson(String key, String version) {
        return """
            {
              "apiVersion": "werkflow.io/connector/v1",
              "kind": "ConnectorDefinition",
              "metadata": {
                "key": "%s",
                "displayName": "Test Connector",
                "version": "%s"
              },
              "spec": {
                "transport": {
                  "type": "rest",
                  "config": { "baseUrl": "https://example.com" }
                },
                "operations": [
                  {
                    "id": "defaultOp",
                    "displayName": "Default Operation",
                    "input": { "type": "object", "properties": { "id": { "type": "string" } } },
                    "output": { "type": "object", "properties": { "result": { "type": "string" } } },
                    "transportSpecific": { "method": "GET", "path": "/" }
                  }
                ]
              }
            }
            """.formatted(key, version);
    }

    private ConnectorDefinitionV2 entity(String key, String version, String json) {
        ConnectorDefinitionV2 e = new ConnectorDefinitionV2();
        e.setKey(key);
        e.setVersion(version);
        e.setTenantId(TENANT);
        e.setDefinitionJson(json);
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        return e;
    }
}
