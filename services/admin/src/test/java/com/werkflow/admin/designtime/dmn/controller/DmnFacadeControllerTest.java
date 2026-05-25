package com.werkflow.admin.designtime.dmn.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.werkflow.admin.designtime.bpmn.service.ProcessVariableScopeService;
import com.werkflow.admin.designtime.platform.client.EngineClient;
import com.werkflow.admin.security.JwtClaimsExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DmnFacadeController} input-column parsing — focused on the
 * XXE hardening of {@code parseDmnInputColumns} and JWT propagation to the engine.
 */
class DmnFacadeControllerTest {

    private static final String TENANT = "tenant-1";
    private static final String TOKEN = "bearer-token-xyz";
    private static final String DMN_ID = "procurementApproval";

    private EngineClient engineClient;
    private JwtClaimsExtractor jwtClaimsExtractor;
    private Jwt jwt;
    private DmnFacadeController controller;

    @BeforeEach
    void setUp() {
        engineClient = mock(EngineClient.class);
        jwtClaimsExtractor = mock(JwtClaimsExtractor.class);
        ProcessVariableScopeService scopeService = mock(ProcessVariableScopeService.class);
        jwt = mock(Jwt.class);

        when(jwtClaimsExtractor.getTenantId(jwt)).thenReturn(TENANT);
        when(jwt.getTokenValue()).thenReturn(TOKEN);

        controller = new DmnFacadeController(scopeService, engineClient, jwtClaimsExtractor, new ObjectMapper());
    }

    @Test
    @DisplayName("getDecisionInputs parses input columns with FEEL types from valid DMN XML")
    void getDecisionInputs_validXml_returnsColumns() {
        String dmnXml = """
                <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/">
                  <decision id="d1">
                    <decisionTable>
                      <input id="in1" label="Amount">
                        <inputExpression typeRef="number"><text>amount</text></inputExpression>
                      </input>
                    </decisionTable>
                  </decision>
                </definitions>
                """;
        when(engineClient.getDmnDefinitionXml(TENANT, DMN_ID, TOKEN)).thenReturn(dmnXml);

        ResponseEntity<List<Map<String, String>>> response = controller.getDecisionInputs(DMN_ID, jwt);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).singleElement().satisfies(col -> {
            assertThat(col.get("id")).isEqualTo("in1");
            assertThat(col.get("label")).isEqualTo("Amount");
            assertThat(col.get("feelType")).isEqualTo("number");
        });
    }

    @Test
    @DisplayName("getDecisionInputs forwards the caller's bearer token to the engine")
    void getDecisionInputs_threadsBearerToken() {
        when(engineClient.getDmnDefinitionXml(TENANT, DMN_ID, TOKEN)).thenReturn("<definitions/>");

        controller.getDecisionInputs(DMN_ID, jwt);

        verify(engineClient).getDmnDefinitionXml(eq(TENANT), eq(DMN_ID), eq(TOKEN));
    }

    @Test
    @DisplayName("getDecisionInputs rejects DOCTYPE/external entities (XXE-safe) without leaking file contents")
    void getDecisionInputs_xxePayload_isNotExpanded(@TempDir Path tmp) throws Exception {
        Path secret = tmp.resolve("secret.txt");
        Files.writeString(secret, "TOP-SECRET-CONTENTS");

        String maliciousXml = """
                <?xml version="1.0"?>
                <!DOCTYPE definitions [ <!ENTITY xxe SYSTEM "file://%s"> ]>
                <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/">
                  <decision id="d1">
                    <decisionTable>
                      <input id="in1" label="&xxe;">
                        <inputExpression typeRef="string"><text>x</text></inputExpression>
                      </input>
                    </decisionTable>
                  </decision>
                </definitions>
                """.formatted(secret.toUri());
        when(engineClient.getDmnDefinitionXml(TENANT, DMN_ID, TOKEN)).thenReturn(maliciousXml);

        ResponseEntity<List<Map<String, String>>> response = controller.getDecisionInputs(DMN_ID, jwt);

        // DOCTYPE is disallowed → parse throws → empty result. (If hardening regressed and the
        // entity expanded, the parse would succeed and produce a column whose label leaks the file.)
        assertThat(response.getBody()).isEmpty();
    }
}
