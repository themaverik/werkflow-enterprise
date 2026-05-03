package com.werkflow.engine.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.BpmnError;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectorCallDelegateTest {

    @Mock RestTemplate serviceRestTemplate;
    @Mock DelegateExecution execution;
    @Mock Expression connectorKeyExpr, pathExpr, methodExpr, bodyExpr,
                      responseVariableExpr, variableMappingsExpr, onErrorExpr;

    ConnectorCallDelegate delegate;

    @BeforeEach
    void setUp() {
        delegate = new ConnectorCallDelegate(serviceRestTemplate, "http://admin:8083");

        delegate.setConnectorKey(connectorKeyExpr);
        delegate.setPath(pathExpr);
        delegate.setMethod(methodExpr);
        delegate.setResponseVariable(responseVariableExpr);
        delegate.setOnError(onErrorExpr);

        lenient().when(execution.getTenantId()).thenReturn("acme");
        lenient().when(connectorKeyExpr.getValue(execution)).thenReturn("erp-connector");
        lenient().when(pathExpr.getValue(execution)).thenReturn("/departments");
        lenient().when(methodExpr.getValue(execution)).thenReturn("GET");
        lenient().when(responseVariableExpr.getValue(execution)).thenReturn("deptResponse");
        lenient().when(onErrorExpr.getValue(execution)).thenReturn("FAIL");
    }

    // --- URL construction ---

    @Test
    void execute_callsAdminWithCorrectUrl() {
        Map<String, Object> adminResponse = Map.of(
            "statusCode", 200,
            "body", "{\"id\":\"d1\",\"name\":\"Finance\"}"
        );
        when(serviceRestTemplate.postForEntity(anyString(), any(), eq(Map.class)))
            .thenReturn(ResponseEntity.ok(adminResponse));

        delegate.execute(execution);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(serviceRestTemplate).postForEntity(urlCaptor.capture(), any(), eq(Map.class));

        String url = urlCaptor.getValue();
        assertThat(url).contains("http://admin:8083/api/connectors/erp-connector/call");
        assertThat(url).contains("tenantCode=acme");
        assertThat(url).contains("path=");
        assertThat(url).contains("method=GET");
    }

    @Test
    void execute_storesResponseBodyInProcessVariable() {
        String responseBody = "{\"id\":\"d1\"}";
        when(serviceRestTemplate.postForEntity(anyString(), any(), eq(Map.class)))
            .thenReturn(ResponseEntity.ok(Map.of("statusCode", 200, "body", responseBody)));

        delegate.execute(execution);

        verify(execution).setVariable("deptResponse", responseBody);
    }

    // --- variable_mappings ---

    @Test
    void execute_appliesVariableMappings() {
        String mappings = "[{\"responseField\":\"id\",\"variableName\":\"deptId\",\"type\":\"string\",\"required\":true}," +
                           "{\"responseField\":\"headCount\",\"variableName\":\"deptSize\",\"type\":\"integer\",\"required\":false}]";

        delegate.setVariableMappings(variableMappingsExpr);
        when(variableMappingsExpr.getValue(execution)).thenReturn(mappings);

        when(serviceRestTemplate.postForEntity(anyString(), any(), eq(Map.class)))
            .thenReturn(ResponseEntity.ok(Map.of("statusCode", 200,
                "body", "{\"id\":\"d1\",\"headCount\":42}")));

        delegate.execute(execution);

        verify(execution).setVariable("deptId", "d1");
        verify(execution).setVariable("deptSize", 42L);
    }

    @Test
    void execute_skipsRequiredMissingFieldWithWarning() {
        String mappings = "[{\"responseField\":\"missingField\",\"variableName\":\"target\",\"type\":\"string\",\"required\":true}]";
        delegate.setVariableMappings(variableMappingsExpr);
        when(variableMappingsExpr.getValue(execution)).thenReturn(mappings);

        when(serviceRestTemplate.postForEntity(anyString(), any(), eq(Map.class)))
            .thenReturn(ResponseEntity.ok(Map.of("statusCode", 200, "body", "{\"other\":\"val\"}")));

        // should not throw — just logs warning
        assertThatCode(() -> delegate.execute(execution)).doesNotThrowAnyException();
        verify(execution, never()).setVariable(eq("target"), any());
    }

    @Test
    void execute_skipsVariableMappingsWhenResponseBodyIsNotJson() {
        String mappings = "[{\"responseField\":\"id\",\"variableName\":\"deptId\",\"type\":\"string\",\"required\":false}]";
        delegate.setVariableMappings(variableMappingsExpr);
        when(variableMappingsExpr.getValue(execution)).thenReturn(mappings);

        when(serviceRestTemplate.postForEntity(anyString(), any(), eq(Map.class)))
            .thenReturn(ResponseEntity.ok(Map.of("statusCode", 200, "body", "NOT_JSON")));

        assertThatCode(() -> delegate.execute(execution)).doesNotThrowAnyException();
        verify(execution, never()).setVariable(eq("deptId"), any());
    }

    // --- validation ---

    @Test
    void execute_throwsWhenConnectorKeyIsBlank() {
        when(connectorKeyExpr.getValue(execution)).thenReturn("");

        assertThatThrownBy(() -> delegate.execute(execution))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("connectorKey");
    }

    @Test
    void execute_throwsWhenPathIsBlank() {
        when(pathExpr.getValue(execution)).thenReturn("  ");

        assertThatThrownBy(() -> delegate.execute(execution))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("path");
    }

    @Test
    void execute_throwsWhenTenantIdMissing() {
        when(execution.getTenantId()).thenReturn(null);

        assertThatThrownBy(() -> delegate.execute(execution))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("tenantId");
    }

    // --- onError modes ---

    @Test
    void execute_continueMode_setsErrorVariableInsteadOfThrowing() {
        when(onErrorExpr.getValue(execution)).thenReturn("CONTINUE");
        when(serviceRestTemplate.postForEntity(anyString(), any(), eq(Map.class)))
            .thenThrow(new RuntimeException("admin unreachable"));

        assertThatCode(() -> delegate.execute(execution)).doesNotThrowAnyException();

        verify(execution).setVariable("deptResponseSuccess", false);
        verify(execution).setVariable(eq("deptResponseError"), anyString());
    }

    @Test
    void execute_throwBpmnErrorMode_throwsBpmnError() {
        when(onErrorExpr.getValue(execution)).thenReturn("THROW_BPMN_ERROR");
        when(serviceRestTemplate.postForEntity(anyString(), any(), eq(Map.class)))
            .thenThrow(new RuntimeException("admin unreachable"));

        assertThatThrownBy(() -> delegate.execute(execution))
            .isInstanceOf(BpmnError.class);
    }

    @Test
    void execute_failMode_wrapsExceptionAsRuntimeException() {
        when(serviceRestTemplate.postForEntity(anyString(), any(), eq(Map.class)))
            .thenThrow(new RuntimeException("admin unreachable"));

        assertThatThrownBy(() -> delegate.execute(execution))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("connectorCallDelegate failed");
    }
}
