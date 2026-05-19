package com.werkflow.engine.action;

import com.werkflow.common.security.SecretsResolver;
import com.werkflow.common.security.SsrfGuard;
import com.werkflow.engine.audit.ProcessAuditLogRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.BpmnError;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.DelegateHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RestConnectorDelegate}.
 *
 * <p>Fields are driven via {@code MockedStatic<DelegateHelper>} rather than
 * {@code @Setter} setters — matching the thread-safe, per-execution field
 * resolution introduced by the F1 fix (field-injection race).
 */
@ExtendWith(MockitoExtension.class)
class RestConnectorDelegateTest {

    @Mock private SsrfGuard ssrfGuard;
    @Mock private ResponseMasker responseMasker;
    @Mock private SecretsResolver secretsResolver;
    @Mock private ProcessAuditLogRepository auditLogRepository;
    @Mock private TenantEndpointResolver endpointResolver;
    @Mock private DelegateExecution execution;
    @Mock private Expression urlExpr, methodExpr, secretRefExpr, responseVarExpr,
                             extractFieldsExpr, maskFieldsExpr, onErrorExpr,
                             connectorExpr, pathExpr, bodyExpr;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private RestConnectorDelegate delegate;

    @BeforeEach
    void setUp() {
        delegate = new RestConnectorDelegate(
            ssrfGuard, responseMasker, secretsResolver, auditLogRepository, meterRegistry, endpointResolver);

        lenient().when(execution.getProcessInstanceId()).thenReturn("proc-1");
        lenient().when(execution.getId()).thenReturn("exec-1");
        lenient().when(execution.getProcessDefinitionId()).thenReturn("myProcess:1:abc");
        lenient().when(execution.getCurrentActivityId()).thenReturn("task1");
        lenient().when(execution.getCurrentActivityName()).thenReturn("Check Stock");
    }

    // -------------------------------------------------------------------------
    // Field-stub helpers
    // -------------------------------------------------------------------------

    /**
     * Stubs all base and REST-specific fields for a legacy-url scenario
     * (no connector, just a direct url + method).
     */
    private void stubLegacyUrlFields(MockedStatic<DelegateHelper> dh,
                                     String url,
                                     String method,
                                     String responseVar,
                                     String onError) {
        stubBaseFields(dh, responseVar, onError);
        stubField(dh, "url",       url,       urlExpr);
        stubField(dh, "method",    method,    methodExpr);
        stubAbsent(dh, "connector");
        stubAbsent(dh, "path");
        stubAbsent(dh, "body");
        stubAbsent(dh, "secretRef");
        stubAbsent(dh, "timeoutSeconds");
    }

    /**
     * Stubs all base and REST-specific fields for a connector+path scenario.
     */
    private void stubConnectorFields(MockedStatic<DelegateHelper> dh,
                                     String connector,
                                     String path,
                                     String method,
                                     String responseVar,
                                     String onError) {
        stubBaseFields(dh, responseVar, onError);
        stubField(dh, "connector", connector, connectorExpr);
        stubField(dh, "path",      path,      pathExpr);
        stubField(dh, "method",    method,    methodExpr);
        stubAbsent(dh, "url");
        stubAbsent(dh, "body");
        stubAbsent(dh, "secretRef");
        stubAbsent(dh, "timeoutSeconds");
    }

    private void stubBaseFields(MockedStatic<DelegateHelper> dh, String responseVar, String onError) {
        stubField(dh, "onError",          onError,      onErrorExpr);
        stubField(dh, "responseVariable", responseVar,  responseVarExpr);
        stubAbsent(dh, "maskFields");
        stubAbsent(dh, "extractFields");
        stubAbsent(dh, "storeRawResponse");
        stubAbsent(dh, "useLocalVariables");
    }

    private void stubField(MockedStatic<DelegateHelper> dh, String name, String value, Expression expr) {
        if (value != null) {
            lenient().when(expr.getValue(execution)).thenReturn(value);
            lenient().when(DelegateHelper.getFieldExpression(execution, name)).thenReturn(expr);
        } else {
            lenient().when(DelegateHelper.getFieldExpression(execution, name)).thenReturn(null);
        }
    }

    private void stubAbsent(MockedStatic<DelegateHelper> dh, String name) {
        lenient().when(DelegateHelper.getFieldExpression(execution, name)).thenReturn(null);
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void execute_callsSsrfGuardBeforeHttpCall() {
        try (MockedStatic<DelegateHelper> dh = mockStatic(DelegateHelper.class)) {
            stubLegacyUrlFields(dh, "https://api.example.com/stock", "GET", "stockResult", "FAIL");

            doThrow(new SecurityException("SSRF blocked"))
                .when(ssrfGuard).validate("https://api.example.com/stock");

            assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("SSRF blocked");

            verify(ssrfGuard).validate("https://api.example.com/stock");
            verify(auditLogRepository).saveAndFlush(any());
        }
    }

    @Test
    void execute_continuesOnErrorWhenConfigured() {
        try (MockedStatic<DelegateHelper> dh = mockStatic(DelegateHelper.class)) {
            stubLegacyUrlFields(dh, "https://api.example.com/stock", "GET", "stockResult", "CONTINUE");

            doThrow(new SecurityException("SSRF blocked"))
                .when(ssrfGuard).validate(anyString());

            assertThatNoException().isThrownBy(() -> delegate.execute(execution));

            verify(execution).setVariable(eq("stockResultSuccess"), eq(false));
        }
    }

    @Test
    void execute_throwsBpmnErrorWhenConfigured() {
        try (MockedStatic<DelegateHelper> dh = mockStatic(DelegateHelper.class)) {
            stubLegacyUrlFields(dh, "https://api.example.com/stock", "GET", "stockResult", "THROW_BPMN_ERROR");

            doThrow(new SecurityException("SSRF blocked"))
                .when(ssrfGuard).validate(anyString());

            assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(BpmnError.class);
        }
    }

    @Test
    void parseExtractFields_parsesValidEntries() {
        // No DelegateHelper needed — parseExtractFields is a pure utility
        RestConnectorDelegate d = new RestConnectorDelegate(
            ssrfGuard, responseMasker, secretsResolver, auditLogRepository, meterRegistry, endpointResolver);
        Map<String, String> parsed = d.parseExtractFields("count:$.stock.count,available:$.stock.available");
        assertThat(parsed).containsEntry("count", "$.stock.count")
                          .containsEntry("available", "$.stock.available");
    }

    @Test
    void execute_usesConnectorPlusPathWhenConnectorPresent() {
        try (MockedStatic<DelegateHelper> dh = mockStatic(DelegateHelper.class)) {
            stubConnectorFields(dh, "procurement", "/api/create-po", "GET", "response", "FAIL");
            when(execution.getTenantId()).thenReturn("tenant-a");
            when(endpointResolver.resolve("tenant-a", "procurement")).thenReturn("http://procurement:8085");
            doThrow(new SecurityException("test-stop")).when(ssrfGuard).validateExternal(anyString());

            assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(RuntimeException.class);

            verify(endpointResolver).resolve("tenant-a", "procurement");
            verify(ssrfGuard).validateExternal("http://procurement:8085/api/create-po");
            verify(ssrfGuard, never()).validate(anyString());
        }
    }

    @Test
    void execute_usesLegacyUrlAndStrictSsrfWhenNoConnector() {
        try (MockedStatic<DelegateHelper> dh = mockStatic(DelegateHelper.class)) {
            stubLegacyUrlFields(dh, "https://api.example.com/resource", "GET", "response", "FAIL");
            doThrow(new SecurityException("test-stop")).when(ssrfGuard).validate(anyString());

            assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(RuntimeException.class);

            verify(ssrfGuard).validate("https://api.example.com/resource");
            verify(ssrfGuard, never()).validateExternal(anyString());
        }
    }

    @Test
    void resolveBodyTemplate_substitutesVariables() {
        RestConnectorDelegate d = new RestConnectorDelegate(
            ssrfGuard, responseMasker, secretsResolver, auditLogRepository, meterRegistry, endpointResolver);

        when(execution.getVariable("requestId")).thenReturn("REQ-001");
        when(execution.getVariable("amount")).thenReturn(5000);

        String result = d.resolveBodyTemplate("{\"requestId\":\"${requestId}\",\"amount\":${amount}}", execution);

        assertThat(result).isEqualTo("{\"requestId\":\"REQ-001\",\"amount\":5000}");
    }

    @Test
    void resolveBodyTemplate_jsonEscapesStringValues() {
        RestConnectorDelegate d = new RestConnectorDelegate(
            ssrfGuard, responseMasker, secretsResolver, auditLogRepository, meterRegistry, endpointResolver);

        when(execution.getVariable("description")).thenReturn("Laptop, 16\" screen");

        String result = d.resolveBodyTemplate("{\"desc\":\"${description}\"}", execution);

        assertThat(result).contains("Laptop, 16\\\" screen");
    }

    @Test
    void resolveBodyTemplate_handlesNullVariable() {
        RestConnectorDelegate d = new RestConnectorDelegate(
            ssrfGuard, responseMasker, secretsResolver, auditLogRepository, meterRegistry, endpointResolver);

        when(execution.getVariable("missingVar")).thenReturn(null);

        String result = d.resolveBodyTemplate("{\"val\":${missingVar}}", execution);

        assertThat(result).isEqualTo("{\"val\":null}");
    }

    @Test
    void execute_throwsWhenConnectorModeAndNoTenantId() {
        try (MockedStatic<DelegateHelper> dh = mockStatic(DelegateHelper.class)) {
            stubConnectorFields(dh, "procurement", "/api/test", "GET", "response", "FAIL");
            when(execution.getTenantId()).thenReturn(null);

            assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("tenantId");
        }
    }

    @Test
    void execute_throwsWhenLegacyModeAndNoUrl() {
        try (MockedStatic<DelegateHelper> dh = mockStatic(DelegateHelper.class)) {
            stubLegacyUrlFields(dh, null, "GET", "response", "FAIL");

            assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void resolveBodyTemplate_escapesControlCharacters() {
        RestConnectorDelegate d = new RestConnectorDelegate(
            ssrfGuard, responseMasker, secretsResolver, auditLogRepository, meterRegistry, endpointResolver);

        when(execution.getVariable("data")).thenReturn("line1\nline2\ttabbed");

        String result = d.resolveBodyTemplate("{\"data\":\"${data}\"}", execution);

        assertThat(result).isEqualTo("{\"data\":\"line1\\nline2\\ttabbed\"}");
    }
}
