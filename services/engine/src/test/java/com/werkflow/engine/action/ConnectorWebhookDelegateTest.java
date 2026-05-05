package com.werkflow.engine.action;

import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.BpmnError;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectorWebhookDelegateTest {

    @Mock RestTemplate serviceRestTemplate;
    @Mock DelegateExecution execution;
    @Mock Expression connectorKeyExpr, pathExpr, bodyExpr, onErrorExpr;

    ConnectorWebhookDelegate delegate;

    @BeforeEach
    void setUp() {
        delegate = new ConnectorWebhookDelegate(serviceRestTemplate, "http://admin:8083");

        delegate.setConnectorKey(connectorKeyExpr);
        delegate.setPath(pathExpr);
        delegate.setOnError(onErrorExpr);

        lenient().when(execution.getTenantId()).thenReturn("acme");
        lenient().when(connectorKeyExpr.getValue(execution)).thenReturn("erp-connector");
        lenient().when(pathExpr.getValue(execution)).thenReturn("/events/order-created");
        lenient().when(onErrorExpr.getValue(execution)).thenReturn("FAIL");
    }

    // --- URL construction ---

    @Test
    void execute_callsAdminWithPostMethodAndCorrectUrl() {
        when(serviceRestTemplate.postForEntity(anyString(), any(), eq(Map.class)))
            .thenReturn(ResponseEntity.ok(Map.of("statusCode", 200, "body", "")));

        delegate.execute(execution);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(serviceRestTemplate).postForEntity(urlCaptor.capture(), any(), eq(Map.class));

        String url = urlCaptor.getValue();
        assertThat(url).contains("http://admin:8083/api/connectors/erp-connector/call");
        assertThat(url).contains("tenantCode=acme");
        assertThat(url).contains("method=POST");
        assertThat(url).contains("path=");
    }

    @Test
    void execute_sendsEmptyJsonBodyWhenBodyExpressionIsNull() {
        // no body expression set — should send "{}" as fallback
        when(serviceRestTemplate.postForEntity(anyString(), any(), eq(Map.class)))
            .thenReturn(ResponseEntity.ok(Map.of("statusCode", 200, "body", "")));

        delegate.execute(execution);

        ArgumentCaptor<Object> entityCaptor = ArgumentCaptor.forClass(Object.class);
        verify(serviceRestTemplate).postForEntity(anyString(), entityCaptor.capture(), eq(Map.class));
        // HttpEntity body is "{}"
        Object entity = entityCaptor.getValue();
        assertThat(entity.toString()).contains("{}");
    }

    @Test
    void execute_sendsCustomBodyWhenBodyExpressionSet() {
        delegate.setBody(bodyExpr);
        when(bodyExpr.getValue(execution)).thenReturn("{\"orderId\":\"ORD-001\"}");
        when(serviceRestTemplate.postForEntity(anyString(), any(), eq(Map.class)))
            .thenReturn(ResponseEntity.ok(Map.of("statusCode", 200, "body", "")));

        delegate.execute(execution);

        ArgumentCaptor<Object> entityCaptor = ArgumentCaptor.forClass(Object.class);
        verify(serviceRestTemplate).postForEntity(anyString(), entityCaptor.capture(), eq(Map.class));
        assertThat(entityCaptor.getValue().toString()).contains("ORD-001");
    }

    // --- non-2xx does not throw (fire-and-forget) ---

    @Test
    void execute_logsWarningOnNon2xxButDoesNotThrow() {
        when(serviceRestTemplate.postForEntity(anyString(), any(), eq(Map.class)))
            .thenReturn(ResponseEntity.ok(Map.of("statusCode", 503, "body", "Service Unavailable")));

        assertThatCode(() -> delegate.execute(execution)).doesNotThrowAnyException();
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
        when(pathExpr.getValue(execution)).thenReturn(null);

        assertThatThrownBy(() -> delegate.execute(execution))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("path");
    }

    @Test
    void execute_throwsWhenTenantIdMissing() {
        when(execution.getTenantId()).thenReturn("");

        assertThatThrownBy(() -> delegate.execute(execution))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("tenantId");
    }

    // --- onError modes ---

    @Test
    void execute_continueMode_doesNotThrowOnFailure() {
        when(onErrorExpr.getValue(execution)).thenReturn("CONTINUE");
        when(serviceRestTemplate.postForEntity(anyString(), any(), eq(Map.class)))
            .thenThrow(new RuntimeException("admin unreachable"));

        assertThatCode(() -> delegate.execute(execution)).doesNotThrowAnyException();
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
            .hasMessageContaining("connectorWebhookDelegate failed");
    }
}
