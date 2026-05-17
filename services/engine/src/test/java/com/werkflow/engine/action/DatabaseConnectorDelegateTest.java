package com.werkflow.engine.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.werkflow.common.security.SecretsResolver;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
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
 */
@ExtendWith(MockitoExtension.class)
class DatabaseConnectorDelegateTest {

    @Mock private ResponseMasker responseMasker;
    @Mock private SecretsResolver secretsResolver;
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
            responseMasker, secretsResolver, auditLogRepository, meterRegistry,
            adminServiceClient, datasourceRegistry, queryExecutor, keysetPaginator);

        delegate.setConnector(connectorExpr);
        delegate.setOperationId(operationIdExpr);
        delegate.setResponseVariable(responseVarExpr);
        delegate.setOnError(onErrorExpr);

        lenient().when(execution.getProcessInstanceId()).thenReturn("proc-1");
        lenient().when(execution.getId()).thenReturn("exec-1");
        lenient().when(execution.getProcessDefinitionId()).thenReturn("myProcess:1:abc");
        lenient().when(execution.getCurrentActivityId()).thenReturn("dbTask1");
        lenient().when(execution.getCurrentActivityName()).thenReturn("Fetch Employees");
        lenient().when(execution.getTenantId()).thenReturn("tenant-a");

        lenient().when(responseMasker.mask(any(), any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void execute_happyPath_storesJsonArrayResult() throws Exception {
        // Arrange
        when(connectorExpr.getValue(execution)).thenReturn("employee-db");
        when(operationIdExpr.getValue(execution)).thenReturn("listEmployees");
        when(responseVarExpr.getValue(execution)).thenReturn("employees");
        when(onErrorExpr.getValue(execution)).thenReturn("FAIL");

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

    @Test
    void execute_dbError_continueMode_storesSuccessFalse() throws Exception {
        // Arrange
        when(connectorExpr.getValue(execution)).thenReturn("employee-db");
        when(operationIdExpr.getValue(execution)).thenReturn("listEmployees");
        when(responseVarExpr.getValue(execution)).thenReturn("employees");
        when(onErrorExpr.getValue(execution)).thenReturn("CONTINUE");

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

    @Test
    void execute_missingConnector_throwsImmediately() {
        when(connectorExpr.getValue(execution)).thenReturn(null);
        when(operationIdExpr.getValue(execution)).thenReturn("listEmployees");
        when(onErrorExpr.getValue(execution)).thenReturn("FAIL");
        when(responseVarExpr.getValue(execution)).thenReturn("response");

        assertThatThrownBy(() -> delegate.execute(execution))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("connector");
    }

    @Test
    void execute_missingOperationId_throwsImmediately() {
        when(connectorExpr.getValue(execution)).thenReturn("employee-db");
        when(operationIdExpr.getValue(execution)).thenReturn("  ");
        when(onErrorExpr.getValue(execution)).thenReturn("FAIL");
        when(responseVarExpr.getValue(execution)).thenReturn("response");

        assertThatThrownBy(() -> delegate.execute(execution))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("operationId");
    }

    @Test
    void execute_missingTenantId_throwsImmediately() {
        when(connectorExpr.getValue(execution)).thenReturn("employee-db");
        when(operationIdExpr.getValue(execution)).thenReturn("listEmployees");
        when(onErrorExpr.getValue(execution)).thenReturn("FAIL");
        when(responseVarExpr.getValue(execution)).thenReturn("response");
        when(execution.getTenantId()).thenReturn(null);

        assertThatThrownBy(() -> delegate.execute(execution))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("tenantId");
    }
}
