package com.werkflow.admin.designtime.bpmn.controller;

import com.werkflow.admin.designtime.bpmn.dto.VariableAtActivityResponse;
import com.werkflow.admin.designtime.bpmn.service.ProcessVariableScopeService;
import com.werkflow.admin.security.JwtClaimsExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Unit tests for {@link BpmnFacadeController} request-parameter guards. */
class BpmnFacadeControllerTest {

    private ProcessVariableScopeService scopeService;
    private BpmnFacadeController controller;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        scopeService = mock(ProcessVariableScopeService.class);
        JwtClaimsExtractor jwtClaimsExtractor = mock(JwtClaimsExtractor.class);
        jwt = mock(Jwt.class);
        controller = new BpmnFacadeController(scopeService, jwtClaimsExtractor);
    }

    @Test
    @DisplayName("variablesAt returns 400 for a blank processDefId without calling the scope service")
    void variablesAt_blankProcessDef_returnsBadRequest() {
        ResponseEntity<VariableAtActivityResponse> response = controller.variablesAt("  ", "act1", jwt);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verify(scopeService, never()).variablesAt(any(), any(), any(), any());
    }

    @Test
    @DisplayName("variablesAt returns 400 for a blank activityId without calling the scope service")
    void variablesAt_blankActivity_returnsBadRequest() {
        ResponseEntity<VariableAtActivityResponse> response = controller.variablesAt("proc:1", "", jwt);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verify(scopeService, never()).variablesAt(any(), any(), any(), any());
    }
}
