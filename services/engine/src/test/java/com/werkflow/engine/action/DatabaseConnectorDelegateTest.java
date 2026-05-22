package com.werkflow.engine.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.werkflow.engine.action.db.DatasourceRegistry;
import com.werkflow.engine.action.db.KeysetPaginator;
import com.werkflow.engine.action.db.NamedQueryExecutor;
import com.werkflow.engine.audit.ProcessAuditLogRepository;
import com.werkflow.engine.client.AdminServiceClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.DelegateHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DatabaseConnectorDelegate} covering:
 * - Happy path: LIST result mode stored as JSON variable
 * - DB error in CONTINUE mode: stores success=false + error message
 * - Missing connector key: throws immediately
 *
 * <p>Fields are driven via {@code MockedStatic<DelegateHelper>} rather than
 * {@code @Setter} setters — matching the thread-safe, per-execution field
 * resolution introduced by the F1 fix (field-injection race).
 */
@ExtendWith(MockitoExtension.class)
class DatabaseConnectorDelegateTest {

    @Mock private ResponseMasker responseMasker;
    @Mock private ProcessAuditLogRepository auditLogRepository;
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    @Mock private AdminServiceClient adminServiceClient;
    @Mock private DatasourceRegistry datasourceRegistry;
    @Mock private NamedQueryExecutor queryExecutor;
    @Mock private KeysetPaginator keysetPaginator;
    @Mock private DelegateExecution execution;

    @Mock private Expression connectorExpr, operationIdExpr, responseVarExpr, onErrorExpr,
                             queryParamsExpr, resultModeExpr;

    private DatabaseConnectorDelegate delegate;

    private static final String SAMPLE_DEFINITION = """
        {
          "metadata": { "key": "employee-db", "version": "1.0.0" },
          "spec": {
            "transport": {
              "type": "database",
              "config": {
                "datasourceRef": "demo-h2",
                "readOnly": true,
                "queries": [
                  {
                    "id": "getEmployees",
                    "sql": "SELECT id, name FROM employees WHERE dept = :dept",
                    "resultMode": "array",
                    "rowLimit": 100,
                    "queryTimeoutSeconds": 5
                  }
                ]
              }
            },
            "operations": [
              {
                "id": "listEmployees",
                "transportSpecific": { "queryRef": "getEmployees" }
              }
            ]
          }
        }
        """;

    @BeforeEach
    void setUp() {
        delegate = new DatabaseConnectorDelegate(
            responseMasker, auditLogRepository, meterRegistry,
            adminServiceClient, datasourceRegistry, queryExecutor, keysetPaginator);

        lenient().when(execution.getProcessInstanceId()).thenReturn("proc-1");
        lenient().when(execution.getId()).thenReturn("exec-1");
        lenient().when(execution.getProcessDefinitionId()).thenReturn("myProcess:1:abc");
        lenient().when(execution.getCurrentActivityId()).thenReturn("dbTask1");
        lenient().when(execution.getCurrentActivityName()).thenReturn("Fetch Employees");
        lenient().when(execution.getTenantId()).thenReturn("tenant-a");

        lenient().when(responseMasker.mask(any(), any())).thenAnswer(inv -> inv.getArgument(0));
    }

    /**
     * Stubs {@link DelegateHelper#getFieldExpression(DelegateExecution, String)} for the
     * named fields used by the base class and this delegate. Any field not listed returns null
     * (which causes {@code getFieldString} to return its default value — matching BPMN behaviour
     * when a {@code <flowable:field>} element is absent).
     */
    private void stubFields(MockedStatic<DelegateHelper> dh,
                            String connectorVal,
                            String operationIdVal,
                            String responseVarVal,
                            String onErrorVal) {
        // Base-class fields
        stubField(dh, "onError",           onErrorVal,      onErrorExpr);
        stubField(dh, "responseVariable",  responseVarVal,  responseVarExpr);
        // Absent base fields — null expression → default value
        lenient().when(DelegateHelper.getFieldExpression(execution, "maskFields")).thenReturn(null);
        lenient().when(DelegateHelper.getFieldExpression(execution, "extractFields")).thenReturn(null);
        lenient().when(DelegateHelper.getFieldExpression(execution, "storeRawResponse")).thenReturn(null);
        lenient().when(DelegateHelper.getFieldExpression(execution, "useLocalVariables")).thenReturn(null);
        // Delegate-specific fields
        stubField(dh, "connector",    connectorVal,    connectorExpr);
        stubField(dh, "operationId",  operationIdVal,  operationIdExpr);
        lenient().when(DelegateHelper.getFieldExpression(execution, "queryParams")).thenReturn(null);
        lenient().when(DelegateHelper.getFieldExpression(execution, "resultMode")).thenReturn(null);
    }

    private void stubField(MockedStatic<DelegateHelper> dh, String name, String value, Expression expr) {
        if (value != null) {
            lenient().when(expr.getValue(execution)).thenReturn(value);
            lenient().when(DelegateHelper.getFieldExpression(execution, name)).thenReturn(expr);
        } else {
            lenient().when(DelegateHelper.getFieldExpression(execution, name)).thenReturn(null);
        }
    }

    @Test
    void execute_happyPath_storesJsonArrayResult() throws Exception {
        try (MockedStatic<DelegateHelper> dh = mockStatic(DelegateHelper.class)) {
            // Arrange
            stubFields(dh, "employee-db", "listEmployees", "employees", "FAIL");

            when(adminServiceClient.getConnectorDefinitionJson("tenant-a", "employee-db"))
                .thenReturn(SAMPLE_DEFINITION);

            DataSource mockDs = mock(DataSource.class);
            when(datasourceRegistry.resolve("tenant-a", "demo-h2")).thenReturn(mockDs);

            CircuitBreaker openCb = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
                .circuitBreaker("test");
            when(datasourceRegistry.circuitBreaker("tenant-a", "employee-db")).thenReturn(openCb);

            List<Map<String, Object>> rows = List.of(
                Map.of("id", 1L, "name", "Alice"),
                Map.of("id", 2L, "name", "Bob")
            );
            when(queryExecutor.executeList(eq(mockDs), anyString(), any(), anyInt(), anyInt(), eq(true)))
                .thenReturn(rows);

            // Act
            delegate.execute(execution);

            // Assert — response variable should be a JSON array string
            ArgumentCaptor<String> varValueCaptor = ArgumentCaptor.forClass(String.class);
            verify(execution).setVariable(eq("employees"), varValueCaptor.capture());
            String stored = varValueCaptor.getValue();
            assertThat(stored).contains("Alice").contains("Bob");
        }
    }

    @Test
    void execute_dbError_continueMode_storesSuccessFalse() throws Exception {
        try (MockedStatic<DelegateHelper> dh = mockStatic(DelegateHelper.class)) {
            // Arrange
            stubFields(dh, "employee-db", "listEmployees", "employees", "CONTINUE");

            when(adminServiceClient.getConnectorDefinitionJson("tenant-a", "employee-db"))
                .thenReturn(SAMPLE_DEFINITION);

            DataSource mockDs = mock(DataSource.class);
            when(datasourceRegistry.resolve("tenant-a", "demo-h2")).thenReturn(mockDs);

            CircuitBreaker openCb = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
                .circuitBreaker("test2");
            when(datasourceRegistry.circuitBreaker("tenant-a", "employee-db")).thenReturn(openCb);

            when(queryExecutor.executeList(any(), any(), any(), anyInt(), anyInt(), anyBoolean()))
                .thenThrow(new RuntimeException("Connection refused"));

            // Act — should not throw
            assertThatNoException().isThrownBy(() -> delegate.execute(execution));

            // Assert
            verify(execution).setVariable(eq("employeesSuccess"), eq(false));
            verify(execution).setVariable(eq("employeesError"), contains("Connection refused"));
        }
    }

    @Test
    void execute_missingConnector_throwsImmediately() {
        try (MockedStatic<DelegateHelper> dh = mockStatic(DelegateHelper.class)) {
            stubFields(dh, null, "listEmployees", "response", "FAIL");

            assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("connector");
        }
    }

    @Test
    void execute_missingOperationId_throwsImmediately() {
        try (MockedStatic<DelegateHelper> dh = mockStatic(DelegateHelper.class)) {
            stubFields(dh, "employee-db", "  ", "response", "FAIL");
            // "  " is non-null so expr is stubbed but evaluates to blank
            when(operationIdExpr.getValue(execution)).thenReturn("  ");

            assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("operationId");
        }
    }

    @Test
    void execute_missingTenantId_throwsImmediately() {
        try (MockedStatic<DelegateHelper> dh = mockStatic(DelegateHelper.class)) {
            stubFields(dh, "employee-db", "listEmployees", "response", "FAIL");
            when(execution.getTenantId()).thenReturn(null);

            assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("tenantId");
        }
    }
}
