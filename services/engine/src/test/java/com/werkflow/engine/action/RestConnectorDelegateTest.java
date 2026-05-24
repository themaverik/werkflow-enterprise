package com.werkflow.engine.action;

import com.werkflow.common.security.SsrfGuard;
import com.werkflow.engine.action.credential.ConnectorCredentialBindingClient;
import com.werkflow.engine.action.credential.CredentialRegistry;
import com.werkflow.engine.action.credential.CredentialType;
import com.werkflow.engine.action.credential.CredentialValues;
import com.werkflow.engine.action.credential.HttpCredentialType;
import com.werkflow.engine.action.credential.dto.ConnectorCredentialBindingDto;
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
    @Mock private CredentialRegistry credentialRegistry;
    @Mock private ConnectorCredentialBindingClient bindingClient;
    @Mock private ProcessAuditLogRepository auditLogRepository;
    @Mock private TenantEndpointResolver endpointResolver;
    @Mock private DelegateExecution execution;
    @Mock private Expression urlExpr, methodExpr, credentialTypeExpr, credentialRefExpr,
                             responseVarExpr, extractFieldsExpr, maskFieldsExpr, onErrorExpr,
                             connectorExpr, pathExpr, bodyExpr;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private RestConnectorDelegate delegate;

    @BeforeEach
    void setUp() {
        delegate = new RestConnectorDelegate(
            ssrfGuard, responseMasker, credentialRegistry, bindingClient,
            auditLogRepository, meterRegistry, endpointResolver);

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
     * (no connector, just a direct url + method). No credential fields.
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
        stubAbsent(dh, "credentialType");
        stubAbsent(dh, "credentialRef");
        stubAbsent(dh, "timeoutSeconds");
    }

    /**
     * Stubs all base and REST-specific fields for a connector+path scenario.
     * No credential fields.
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
        stubAbsent(dh, "credentialType");
        stubAbsent(dh, "credentialRef");
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
    // Existing tests — unchanged in behaviour, updated to use CredentialRegistry
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
        Map<String, String> parsed = delegate.parseExtractFields("count:$.stock.count,available:$.stock.available");
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
        when(execution.getVariable("requestId")).thenReturn("REQ-001");
        when(execution.getVariable("amount")).thenReturn(5000);

        String result = delegate.resolveBodyTemplate("{\"requestId\":\"${requestId}\",\"amount\":${amount}}", execution);

        assertThat(result).isEqualTo("{\"requestId\":\"REQ-001\",\"amount\":5000}");
    }

    @Test
    void resolveBodyTemplate_jsonEscapesStringValues() {
        when(execution.getVariable("description")).thenReturn("Laptop, 16\" screen");

        String result = delegate.resolveBodyTemplate("{\"desc\":\"${description}\"}", execution);

        assertThat(result).contains("Laptop, 16\\\" screen");
    }

    @Test
    void resolveBodyTemplate_handlesNullVariable() {
        when(execution.getVariable("missingVar")).thenReturn(null);

        String result = delegate.resolveBodyTemplate("{\"val\":${missingVar}}", execution);

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
        when(execution.getVariable("data")).thenReturn("line1\nline2\ttabbed");

        String result = delegate.resolveBodyTemplate("{\"data\":\"${data}\"}", execution);

        assertThat(result).isEqualTo("{\"data\":\"line1\\nline2\\ttabbed\"}");
    }

    // -------------------------------------------------------------------------
    // New tests — B.4 secretRef hard-cut + credential path
    // -------------------------------------------------------------------------

    /**
     * Any BPMN containing a {@code secretRef} field element (even blank value) must
     * cause an {@link IllegalStateException} with the migration-help message.
     * The check fires at the top of execute() before any other field is read,
     * so only the secretRef stub is needed.
     */
    @Test
    void execute_throwsIllegalStateWhenLegacySecretRefFieldPresent() {
        try (MockedStatic<DelegateHelper> dh = mockStatic(DelegateHelper.class)) {
            Expression legacyExpr = mock(Expression.class);
            lenient().when(DelegateHelper.getFieldExpression(execution, "secretRef")).thenReturn(legacyExpr);

            assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secretRef field is no longer supported")
                .hasMessageContaining("ADR-020");
        }
    }

    /**
     * Present-but-blank secretRef element should also trigger the migration error,
     * because the BPMN element itself (not its value) is the indicator of a stale design.
     */
    @Test
    void execute_throwsIllegalStateWhenSecretRefFieldPresentButBlank() {
        try (MockedStatic<DelegateHelper> dh = mockStatic(DelegateHelper.class)) {
            Expression legacyExpr = mock(Expression.class);
            lenient().when(DelegateHelper.getFieldExpression(execution, "secretRef")).thenReturn(legacyExpr);

            assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secretRef field is no longer supported");
        }
    }

    /**
     * credentialRef present but credentialType absent → {@link IllegalArgumentException}
     * naming the missing field. Credential validation runs before SSRF, so no ssrfGuard
     * stub is needed.
     */
    @Test
    void execute_throwsWhenCredentialRefSetButTypeAbsent() {
        try (MockedStatic<DelegateHelper> dh = mockStatic(DelegateHelper.class)) {
            stubLegacyUrlFields(dh, "https://api.example.com/stock", "GET", "response", "FAIL");
            // Override: credentialRef present, credentialType stays absent
            lenient().when(DelegateHelper.getFieldExpression(execution, "credentialRef"))
                .thenReturn(credentialRefExpr);
            lenient().when(credentialRefExpr.getValue(execution)).thenReturn("my-api-key");

            assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credentialType");
        }
    }

    /**
     * credentialType present but credentialRef absent → {@link IllegalArgumentException}
     * naming the missing field. Credential validation runs before SSRF, so no ssrfGuard
     * stub is needed.
     */
    @Test
    void execute_throwsWhenCredentialTypeSetButRefAbsent() {
        try (MockedStatic<DelegateHelper> dh = mockStatic(DelegateHelper.class)) {
            stubLegacyUrlFields(dh, "https://api.example.com/stock", "GET", "response", "FAIL");
            // Override: credentialType present, credentialRef stays absent
            lenient().when(DelegateHelper.getFieldExpression(execution, "credentialType"))
                .thenReturn(credentialTypeExpr);
            lenient().when(credentialTypeExpr.getValue(execution)).thenReturn("http-basic-auth");

            assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credentialRef");
        }
    }

    /**
     * credentialType resolves to a type that does NOT implement {@link HttpCredentialType}
     * (e.g. a database credential) → {@link IllegalArgumentException} naming the type.
     * Credential validation runs before SSRF, so no ssrfGuard stub is needed.
     */
    @Test
    void execute_throwsWhenCredentialTypeIsNotHttpCredentialType() {
        try (MockedStatic<DelegateHelper> dh = mockStatic(DelegateHelper.class)) {
            stubLegacyUrlFields(dh, "https://api.example.com/stock", "GET", "response", "FAIL");
            lenient().when(DelegateHelper.getFieldExpression(execution, "credentialType"))
                .thenReturn(credentialTypeExpr);
            lenient().when(credentialTypeExpr.getValue(execution)).thenReturn("database");
            lenient().when(DelegateHelper.getFieldExpression(execution, "credentialRef"))
                .thenReturn(credentialRefExpr);
            lenient().when(credentialRefExpr.getValue(execution)).thenReturn("my-db-cred");

            // Registry returns a non-HTTP credential type
            CredentialType nonHttpType = mock(CredentialType.class);
            when(credentialRegistry.get("database")).thenReturn(nonHttpType);

            assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HttpCredentialType");
        }
    }

    /**
     * Both credentialType and credentialRef absent → unauthenticated call proceeds
     * past credential resolution (SSRF guard terminates the call with a test stop;
     * no auth-related exception is thrown).
     */
    @Test
    void execute_unauthenticatedCallProceedsWhenBothCredentialFieldsAbsent() {
        try (MockedStatic<DelegateHelper> dh = mockStatic(DelegateHelper.class)) {
            stubLegacyUrlFields(dh, "https://api.example.com/public", "GET", "response", "FAIL");
            doThrow(new SecurityException("test-stop")).when(ssrfGuard).validate(anyString());

            // Should throw from the SSRF guard, NOT from credential validation —
            // proves credential block was skipped cleanly.
            assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("test-stop");

            verifyNoInteractions(credentialRegistry);
        }
    }

    /**
     * Happy credential path: credentialType + credentialRef both present, type is a valid
     * {@link HttpCredentialType}. Verifies that {@link HttpCredentialType#applyTo} is called
     * with the resolved values before the HTTP dispatch. The actual HTTP call fails because
     * the URL is not real — we assert on the applyTo invocation, not the HTTP outcome.
     */
    @Test
    void execute_appliesHttpCredentialWhenBothFieldsPresent() {
        try (MockedStatic<DelegateHelper> dh = mockStatic(DelegateHelper.class)) {
            stubLegacyUrlFields(dh, "https://api.example.com/secure", "GET", "response", "FAIL");
            when(execution.getTenantId()).thenReturn("tenant-a");

            lenient().when(DelegateHelper.getFieldExpression(execution, "credentialType"))
                .thenReturn(credentialTypeExpr);
            lenient().when(credentialTypeExpr.getValue(execution)).thenReturn("http-header-auth");
            lenient().when(DelegateHelper.getFieldExpression(execution, "credentialRef"))
                .thenReturn(credentialRefExpr);
            lenient().when(credentialRefExpr.getValue(execution)).thenReturn("my-api-key-label");

            HttpCredentialType fakeType = mock(HttpCredentialType.class);
            when(credentialRegistry.get("http-header-auth")).thenReturn(fakeType);

            CredentialValues fakeValues = CredentialValues.of(Map.of("apiKey", "secret-value"));
            when(credentialRegistry.resolveForTenant("http-header-auth", "tenant-a", "my-api-key-label"))
                .thenReturn(fakeValues);

            // ssrfGuard.validate() does nothing (Mockito default for void mock) — call proceeds
            // past SSRF. applyTo is invoked before makeHttpCall. makeHttpCall then throws
            // because the URL is not reachable in unit tests. We verify applyTo was called
            // regardless of the HTTP outcome.
            try {
                delegate.execute(execution);
            } catch (Exception ignored) {
                // Network failure expected in unit test environment — not the subject of this test
            }

            verify(fakeType).applyTo(any(), eq(fakeValues));
        }
    }

    // -------------------------------------------------------------------------
    // New tests — ADR-024 connector-mode credential resolution (Model A)
    // -------------------------------------------------------------------------

    /**
     * Connector mode with no per-task credential fields: the engine resolves the registered
     * connector's own credential binding from admin and applies it. Verifies
     * {@link HttpCredentialType#applyTo} is invoked with the resolved values before HTTP dispatch.
     */
    @Test
    void execute_connectorMode_appliesRegisteredConnectorCredential() {
        try (MockedStatic<DelegateHelper> dh = mockStatic(DelegateHelper.class)) {
            stubConnectorFields(dh, "crm-api", "/v2/contacts", "GET", "response", "FAIL");
            when(execution.getTenantId()).thenReturn("tenant-a");
            when(endpointResolver.resolve("tenant-a", "crm-api")).thenReturn("https://crm.example.io");

            when(bindingClient.resolveBinding("tenant-a", "crm-api"))
                .thenReturn(java.util.Optional.of(
                    new ConnectorCredentialBindingDto("http-bearer-token", "prod-token")));

            HttpCredentialType fakeType = mock(HttpCredentialType.class);
            when(credentialRegistry.get("http-bearer-token")).thenReturn(fakeType);
            CredentialValues fakeValues = CredentialValues.of(Map.of("token", "sk-123"));
            when(credentialRegistry.resolveForTenant("http-bearer-token", "tenant-a", "prod-token"))
                .thenReturn(fakeValues);

            // SSRF passes (validateExternal default no-op); applyTo runs before the unreachable
            // makeHttpCall. We assert on applyTo regardless of the HTTP outcome.
            try {
                delegate.execute(execution);
            } catch (Exception ignored) {
                // Network failure expected in unit test environment
            }

            verify(fakeType).applyTo(any(), eq(fakeValues));
        }
    }

    /**
     * Connector mode where the connector requires no auth (binding empty — authScheme NONE,
     * unregistered, or no bound credential): no credential is resolved or applied, and the call
     * proceeds unauthenticated. No exception from the credential path.
     */
    @Test
    void execute_connectorMode_appliesNoAuthWhenBindingEmpty() {
        try (MockedStatic<DelegateHelper> dh = mockStatic(DelegateHelper.class)) {
            stubConnectorFields(dh, "public-api", "/status", "GET", "response", "FAIL");
            when(execution.getTenantId()).thenReturn("tenant-a");
            when(endpointResolver.resolve("tenant-a", "public-api")).thenReturn("https://public.example.io");
            when(bindingClient.resolveBinding("tenant-a", "public-api"))
                .thenReturn(java.util.Optional.empty());

            try {
                delegate.execute(execution);
            } catch (Exception ignored) {
                // Network failure expected in unit test environment
            }

            verify(bindingClient).resolveBinding("tenant-a", "public-api");
            verify(credentialRegistry, never()).get(anyString());
            verify(credentialRegistry, never()).resolveForTenant(anyString(), anyString(), anyString());
        }
    }

    /**
     * Precedence (ADR-024): explicit per-task credential fields win over the connector binding.
     * When a task carries both a connector and per-task credentialType/credentialRef, the per-task
     * credential is applied and the connector binding is never consulted.
     */
    @Test
    void execute_connectorMode_perTaskCredentialWinsOverConnectorBinding() {
        try (MockedStatic<DelegateHelper> dh = mockStatic(DelegateHelper.class)) {
            stubConnectorFields(dh, "crm-api", "/v2/contacts", "GET", "response", "FAIL");
            when(execution.getTenantId()).thenReturn("tenant-a");
            when(endpointResolver.resolve("tenant-a", "crm-api")).thenReturn("https://crm.example.io");

            // Override: per-task credential fields present
            lenient().when(DelegateHelper.getFieldExpression(execution, "credentialType"))
                .thenReturn(credentialTypeExpr);
            lenient().when(credentialTypeExpr.getValue(execution)).thenReturn("http-header-auth");
            lenient().when(DelegateHelper.getFieldExpression(execution, "credentialRef"))
                .thenReturn(credentialRefExpr);
            lenient().when(credentialRefExpr.getValue(execution)).thenReturn("per-task-key");

            HttpCredentialType fakeType = mock(HttpCredentialType.class);
            when(credentialRegistry.get("http-header-auth")).thenReturn(fakeType);
            CredentialValues fakeValues = CredentialValues.of(Map.of("headerName", "X-Api-Key", "headerValue", "v"));
            when(credentialRegistry.resolveForTenant("http-header-auth", "tenant-a", "per-task-key"))
                .thenReturn(fakeValues);

            try {
                delegate.execute(execution);
            } catch (Exception ignored) {
                // Network failure expected in unit test environment
            }

            verify(fakeType).applyTo(any(), eq(fakeValues));
            verifyNoInteractions(bindingClient);
        }
    }

    /**
     * A connector bound to a non-HTTP credential type signals a registry misconfiguration and is
     * rejected with {@link IllegalStateException} (admin only maps HTTP authSchemes, so this
     * should never occur in practice).
     */
    @Test
    void execute_connectorMode_throwsWhenBoundTypeIsNotHttp() {
        try (MockedStatic<DelegateHelper> dh = mockStatic(DelegateHelper.class)) {
            stubConnectorFields(dh, "crm-api", "/v2/contacts", "GET", "response", "FAIL");
            when(execution.getTenantId()).thenReturn("tenant-a");
            when(endpointResolver.resolve("tenant-a", "crm-api")).thenReturn("https://crm.example.io");

            when(bindingClient.resolveBinding("tenant-a", "crm-api"))
                .thenReturn(java.util.Optional.of(
                    new ConnectorCredentialBindingDto("database", "some-ref")));
            CredentialType nonHttpType = mock(CredentialType.class);
            when(credentialRegistry.get("database")).thenReturn(nonHttpType);

            // IllegalStateException is an operator-config error — rethrown directly, not via onError.
            assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HttpCredentialType");
        }
    }

    /**
     * HIGH-3: a connector binding that resolves but whose credential is missing/rotated-away in
     * OpenBao must FAIL the task — never silently degrade to an unauthenticated call — even under
     * {@code onError=CONTINUE}. {@link CredentialResolutionException} is re-thrown, not routed
     * through onError handling.
     */
    @Test
    void execute_connectorMode_missingCredentialFailsTaskEvenWhenOnErrorContinue() {
        try (MockedStatic<DelegateHelper> dh = mockStatic(DelegateHelper.class)) {
            stubConnectorFields(dh, "crm-api", "/v2/contacts", "GET", "response", "CONTINUE");
            when(execution.getTenantId()).thenReturn("tenant-a");
            when(endpointResolver.resolve("tenant-a", "crm-api")).thenReturn("https://crm.example.io");

            when(bindingClient.resolveBinding("tenant-a", "crm-api"))
                .thenReturn(java.util.Optional.of(
                    new ConnectorCredentialBindingDto("http-bearer-token", "rotated-away")));
            HttpCredentialType fakeType = mock(HttpCredentialType.class);
            when(credentialRegistry.get("http-bearer-token")).thenReturn(fakeType);
            when(credentialRegistry.resolveForTenant("http-bearer-token", "tenant-a", "rotated-away"))
                .thenThrow(new com.werkflow.engine.action.credential.CredentialResolutionException(
                    "Credential not configured"));

            assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(com.werkflow.engine.action.credential.CredentialResolutionException.class);

            // Did NOT swallow into onError=CONTINUE success-flag write
            verify(execution, never()).setVariable(eq("responseSuccess"), eq(false));
        }
    }
}
