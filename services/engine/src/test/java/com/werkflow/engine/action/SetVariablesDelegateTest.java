package com.werkflow.engine.action;

import com.werkflow.engine.audit.ProcessAuditLogRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.flowable.bpmn.model.FieldExtension;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.common.engine.impl.el.ExpressionManager;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.DelegateHelper;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SetVariablesDelegateTest {

    // ProcessEngineConfigurationImpl implements HasExpressionManagerEngineConfiguration,
    // which is the interface used by SetVariablesDelegate to obtain the ExpressionManager.
    @Mock private ProcessEngineConfigurationImpl engineConfig;
    @Mock private ExpressionManager expressionManager;
    @Mock private Expression firstExpr;
    @Mock private Expression secondExpr;
    @Mock private DelegateExecution execution;
    // Added in Service-Task audit (F1): SetVariablesDelegate now requires audit + metrics dependencies.
    @Mock private ProcessAuditLogRepository auditLogRepository;
    @Mock private MeterRegistry meterRegistry;

    private SetVariablesDelegate delegate;

    @BeforeEach
    void setUp() {
        when(engineConfig.getExpressionManager()).thenReturn(expressionManager);
        delegate = new SetVariablesDelegate(engineConfig, auditLogRepository, meterRegistry);
    }

    private FieldExtension literalField(String name, String value) {
        FieldExtension f = new FieldExtension();
        f.setFieldName(name);
        f.setStringValue(value);
        return f;
    }

    private FieldExtension expressionField(String name, String expr) {
        FieldExtension f = new FieldExtension();
        f.setFieldName(name);
        f.setExpression(expr);
        return f;
    }

    @Test
    @DisplayName("execute sets literal variables from flowable fields")
    void execute_setsLiteralVariablesFromFlowableFields() {
        FieldExtension field = literalField("var.status", "approved");
        try (MockedStatic<DelegateHelper> mocked = mockStatic(DelegateHelper.class)) {
            mocked.when(() -> DelegateHelper.getFlowElementFields(execution))
                    .thenReturn(List.of(field));

            delegate.execute(execution);

            verify(execution).setVariable("status", "approved");
        }
    }

    @Test
    @DisplayName("execute resolves expression from var-prefixed field")
    void execute_resolvesExpressionFromVarPrefixedField() {
        FieldExtension field = expressionField("var.assignee", "${initiator}");
        when(expressionManager.createExpression("${initiator}")).thenReturn(firstExpr);
        when(firstExpr.getValue(execution)).thenReturn("alice");

        try (MockedStatic<DelegateHelper> mocked = mockStatic(DelegateHelper.class)) {
            mocked.when(() -> DelegateHelper.getFlowElementFields(execution))
                    .thenReturn(List.of(field));

            delegate.execute(execution);

            verify(execution).setVariable("assignee", "alice");
        }
    }

    @Test
    @DisplayName("execute throws on blank variable name after var. prefix")
    void execute_throwsOnBlankVariableNameAfterPrefix() {
        FieldExtension field = literalField("var.", "value");

        try (MockedStatic<DelegateHelper> mocked = mockStatic(DelegateHelper.class)) {
            mocked.when(() -> DelegateHelper.getFlowElementFields(execution))
                    .thenReturn(List.of(field));

            assertThatThrownBy(() -> delegate.execute(execution))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("var.");
        }
    }

    @Test
    @DisplayName("execute skips fields without var. prefix")
    void execute_skipsFieldsWithoutVarPrefix() {
        FieldExtension varField = literalField("var.result", "done");
        FieldExtension otherField = literalField("channel", "email");

        try (MockedStatic<DelegateHelper> mocked = mockStatic(DelegateHelper.class)) {
            mocked.when(() -> DelegateHelper.getFlowElementFields(execution))
                    .thenReturn(List.of(varField, otherField));

            delegate.execute(execution);

            verify(execution).setVariable("result", "done");
            verify(execution, never()).setVariable(eq("channel"), any());
        }
    }

    @Test
    @DisplayName("execute applies all assignments atomically — no variables set when second expression throws")
    void execute_appliesAllAssignmentsAtomically() {
        FieldExtension first = expressionField("var.first", "${ok}");
        FieldExtension second = expressionField("var.second", "${bad}");

        when(expressionManager.createExpression("${ok}")).thenReturn(firstExpr);
        when(expressionManager.createExpression("${bad}")).thenReturn(secondExpr);
        when(firstExpr.getValue(execution)).thenReturn("value1");
        when(secondExpr.getValue(execution)).thenThrow(new RuntimeException("evaluation failed"));

        try (MockedStatic<DelegateHelper> mocked = mockStatic(DelegateHelper.class)) {
            mocked.when(() -> DelegateHelper.getFlowElementFields(execution))
                    .thenReturn(List.of(first, second));

            assertThatThrownBy(() -> delegate.execute(execution))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("evaluation failed");

            // No variables should have been set — atomicity enforced
            verify(execution, never()).setVariable(anyString(), any());
        }
    }
}
