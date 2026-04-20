package com.werkflow.engine.action;

import com.werkflow.common.security.SecretsResolver;
import com.werkflow.common.security.SsrfGuard;
import com.werkflow.engine.audit.ProcessAuditLog;
import com.werkflow.engine.audit.ProcessAuditLogRepository;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.BpmnError;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalApiCallDelegateTest {

    @Mock private SsrfGuard ssrfGuard;
    @Mock private ResponseMasker responseMasker;
    @Mock private SecretsResolver secretsResolver;
    @Mock private ProcessAuditLogRepository auditLogRepository;
    @Mock private RestClient.Builder restClientBuilder;
    @Mock private RestClient restClient;
    @Mock private TenantEndpointResolver endpointResolver;
    @Mock private DelegateExecution execution;
    @Mock private Expression urlExpr, methodExpr, secretRefExpr, responseVarExpr,
                             extractFieldsExpr, maskFieldsExpr, onErrorExpr,
                             connectorExpr, pathExpr, bodyExpr;

    private ExternalApiCallDelegate delegate;

    @BeforeEach
    void setUp() {
        when(restClientBuilder.requestFactory(any())).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);

        delegate = new ExternalApiCallDelegate(
            ssrfGuard, responseMasker, secretsResolver, auditLogRepository, restClientBuilder, endpointResolver);

        delegate.setUrl(urlExpr);
        delegate.setMethod(methodExpr);
        delegate.setResponseVariable(responseVarExpr);
        delegate.setOnError(onErrorExpr);

        lenient().when(execution.getProcessInstanceId()).thenReturn("proc-1");
        lenient().when(execution.getId()).thenReturn("exec-1");
        lenient().when(execution.getProcessDefinitionId()).thenReturn("myProcess:1:abc");
        lenient().when(execution.getCurrentActivityId()).thenReturn("task1");
        lenient().when(execution.getCurrentActivityName()).thenReturn("Check Stock");
    }

    @Test
    void execute_callsSsrfGuardBeforeHttpCall() {
        when(urlExpr.getValue(execution)).thenReturn("https://api.example.com/stock");
        when(methodExpr.getValue(execution)).thenReturn("GET");
        when(responseVarExpr.getValue(execution)).thenReturn("stockResult");
        when(onErrorExpr.getValue(execution)).thenReturn("FAIL");

        doThrow(new SecurityException("SSRF blocked"))
            .when(ssrfGuard).validate("https://api.example.com/stock");

        assertThatThrownBy(() -> delegate.execute(execution))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("SSRF blocked");

        verify(ssrfGuard).validate("https://api.example.com/stock");
        verify(auditLogRepository).save(any());
    }

    @Test
    void execute_continuesOnErrorWhenConfigured() {
        when(urlExpr.getValue(execution)).thenReturn("https://api.example.com/stock");
        when(methodExpr.getValue(execution)).thenReturn("GET");
        when(responseVarExpr.getValue(execution)).thenReturn("stockResult");
        when(onErrorExpr.getValue(execution)).thenReturn("CONTINUE");

        doThrow(new SecurityException("SSRF blocked"))
            .when(ssrfGuard).validate(anyString());

        assertThatNoException().isThrownBy(() -> delegate.execute(execution));

        verify(execution).setVariable(eq("stockResultSuccess"), eq(false));
    }

    @Test
    void execute_throwsBpmnErrorWhenConfigured() {
        when(urlExpr.getValue(execution)).thenReturn("https://api.example.com/stock");
        when(methodExpr.getValue(execution)).thenReturn("GET");
        when(responseVarExpr.getValue(execution)).thenReturn("stockResult");
        when(onErrorExpr.getValue(execution)).thenReturn("THROW_BPMN_ERROR");

        doThrow(new SecurityException("SSRF blocked"))
            .when(ssrfGuard).validate(anyString());

        assertThatThrownBy(() -> delegate.execute(execution))
            .isInstanceOf(BpmnError.class);
    }

    @Test
    void parseExtractFields_parsesValidEntries() {
        ExternalApiCallDelegate d = new ExternalApiCallDelegate(
            ssrfGuard, responseMasker, secretsResolver, auditLogRepository, restClientBuilder, endpointResolver);
        Map<String, String> parsed = d.parseExtractFieldsForTest("count:$.stock.count,available:$.stock.available");
        assertThat(parsed).containsEntry("count", "$.stock.count")
                          .containsEntry("available", "$.stock.available");
    }

    @Test
    void execute_usesConnectorPlusPathWhenConnectorPresent() {
        when(connectorExpr.getValue(execution)).thenReturn("procurement");
        when(pathExpr.getValue(execution)).thenReturn("/api/create-po");
        when(methodExpr.getValue(execution)).thenReturn("GET");
        when(onErrorExpr.getValue(execution)).thenReturn("FAIL");
        when(responseVarExpr.getValue(execution)).thenReturn("response");
        when(execution.getTenantId()).thenReturn("tenant-a");
        when(endpointResolver.resolve("tenant-a", "procurement")).thenReturn("http://procurement:8085");
        doThrow(new SecurityException("test-stop")).when(ssrfGuard).validateExternal(anyString());

        delegate.setConnector(connectorExpr);
        delegate.setPath(pathExpr);

        assertThatThrownBy(() -> delegate.execute(execution))
            .isInstanceOf(RuntimeException.class);

        verify(endpointResolver).resolve("tenant-a", "procurement");
        verify(ssrfGuard).validateExternal("http://procurement:8085/api/create-po");
        verify(ssrfGuard, never()).validate(anyString());
    }

    @Test
    void execute_usesLegacyUrlAndStrictSsrfWhenNoConnector() {
        when(urlExpr.getValue(execution)).thenReturn("https://api.example.com/resource");
        when(methodExpr.getValue(execution)).thenReturn("GET");
        when(onErrorExpr.getValue(execution)).thenReturn("FAIL");
        when(responseVarExpr.getValue(execution)).thenReturn("response");
        doThrow(new SecurityException("test-stop")).when(ssrfGuard).validate(anyString());

        assertThatThrownBy(() -> delegate.execute(execution))
            .isInstanceOf(RuntimeException.class);

        verify(ssrfGuard).validate("https://api.example.com/resource");
        verify(ssrfGuard, never()).validateExternal(anyString());
    }

    @Test
    void resolveBodyTemplate_substitutesVariables() {
        ExternalApiCallDelegate d = new ExternalApiCallDelegate(
            ssrfGuard, responseMasker, secretsResolver, auditLogRepository, restClientBuilder, endpointResolver);

        when(execution.getVariable("requestId")).thenReturn("REQ-001");
        when(execution.getVariable("amount")).thenReturn(5000);

        String result = d.resolveBodyTemplate("{\"requestId\":\"${requestId}\",\"amount\":${amount}}", execution);

        assertThat(result).isEqualTo("{\"requestId\":\"REQ-001\",\"amount\":5000}");
    }

    @Test
    void resolveBodyTemplate_jsonEscapesStringValues() {
        ExternalApiCallDelegate d = new ExternalApiCallDelegate(
            ssrfGuard, responseMasker, secretsResolver, auditLogRepository, restClientBuilder, endpointResolver);

        when(execution.getVariable("description")).thenReturn("Laptop, 16\" screen");

        String result = d.resolveBodyTemplate("{\"desc\":\"${description}\"}", execution);

        assertThat(result).contains("Laptop, 16\\\" screen");
    }

    @Test
    void resolveBodyTemplate_handlesNullVariable() {
        ExternalApiCallDelegate d = new ExternalApiCallDelegate(
            ssrfGuard, responseMasker, secretsResolver, auditLogRepository, restClientBuilder, endpointResolver);

        when(execution.getVariable("missingVar")).thenReturn(null);

        String result = d.resolveBodyTemplate("{\"val\":${missingVar}}", execution);

        assertThat(result).isEqualTo("{\"val\":null}");
    }

    @Test
    void execute_throwsWhenConnectorModeAndNoTenantId() {
        when(connectorExpr.getValue(execution)).thenReturn("procurement");
        lenient().when(pathExpr.getValue(execution)).thenReturn("/api/test");
        lenient().when(methodExpr.getValue(execution)).thenReturn("GET");
        when(onErrorExpr.getValue(execution)).thenReturn("FAIL");
        lenient().when(responseVarExpr.getValue(execution)).thenReturn("response");
        when(execution.getTenantId()).thenReturn(null);

        delegate.setConnector(connectorExpr);
        delegate.setPath(pathExpr);

        assertThatThrownBy(() -> delegate.execute(execution))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("tenantId");
    }

    @Test
    void execute_throwsWhenLegacyModeAndNoUrl() {
        when(urlExpr.getValue(execution)).thenReturn(null);
        when(methodExpr.getValue(execution)).thenReturn("GET");
        when(onErrorExpr.getValue(execution)).thenReturn("FAIL");
        when(responseVarExpr.getValue(execution)).thenReturn("response");

        assertThatThrownBy(() -> delegate.execute(execution))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void resolveBodyTemplate_escapesControlCharacters() {
        ExternalApiCallDelegate d = new ExternalApiCallDelegate(
            ssrfGuard, responseMasker, secretsResolver, auditLogRepository, restClientBuilder, endpointResolver);

        when(execution.getVariable("data")).thenReturn("line1\nline2\ttabbed");

        String result = d.resolveBodyTemplate("{\"data\":\"${data}\"}", execution);

        assertThat(result).isEqualTo("{\"data\":\"line1\\nline2\\ttabbed\"}");
    }
}
