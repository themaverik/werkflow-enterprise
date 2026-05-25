package com.werkflow.admin.designtime.form.controller;

import com.werkflow.admin.designtime.bpmn.service.ProcessVariableScopeService;
import com.werkflow.admin.designtime.connector.service.ConnectorCatalogService;
import com.werkflow.admin.security.JwtClaimsExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Unit tests for {@link FormFacadeController} request-parameter guards. */
class FormFacadeControllerTest {

    private ProcessVariableScopeService scopeService;
    private ConnectorCatalogService catalogService;
    private FormFacadeController controller;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        scopeService = mock(ProcessVariableScopeService.class);
        catalogService = mock(ConnectorCatalogService.class);
        JwtClaimsExtractor jwtClaimsExtractor = mock(JwtClaimsExtractor.class);
        jwt = mock(Jwt.class);
        controller = new FormFacadeController(scopeService, catalogService, jwtClaimsExtractor);
    }

    @Test
    @DisplayName("getBindingTargets returns 400 for a blank processDefId without calling the scope service")
    void getBindingTargets_blankProcessDef_returnsBadRequest() {
        var response = controller.getBindingTargets("  ", "task1", jwt);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verify(scopeService, never()).variablesAt(any(), any(), any(), any());
    }

    @Test
    @DisplayName("getBindingTargets returns 400 for a blank taskId without calling the scope service")
    void getBindingTargets_blankTask_returnsBadRequest() {
        var response = controller.getBindingTargets("proc:1", "  ", jwt);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verify(scopeService, never()).variablesAt(any(), any(), any(), any());
    }

    @Test
    @DisplayName("getConnectorOptions returns 400 for a blank connectorKey without calling the catalog")
    void getConnectorOptions_blankConnector_returnsBadRequest() {
        ResponseEntity<List<Map<String, Object>>> response = controller.getConnectorOptions("", "op1", jwt);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verify(catalogService, never()).getFlatFields(any(), any(), any(), any());
    }

    @Test
    @DisplayName("getConnectorOptions returns 400 for a blank operationId without calling the catalog")
    void getConnectorOptions_blankOperation_returnsBadRequest() {
        ResponseEntity<List<Map<String, Object>>> response = controller.getConnectorOptions("conn", "  ", jwt);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verify(catalogService, never()).getFlatFields(any(), any(), any(), any());
    }
}
