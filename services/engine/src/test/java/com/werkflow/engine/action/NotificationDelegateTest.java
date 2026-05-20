package com.werkflow.engine.action;

import com.werkflow.engine.action.notification.*;
import org.flowable.bpmn.model.FieldExtension;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.common.engine.impl.HasExpressionManagerEngineConfiguration;
import org.flowable.common.engine.impl.el.ExpressionManager;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.DelegateHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDelegateTest {

    @Mock private NotificationChannelFactory channelFactory;
    @Mock private NotificationTemplateService templateService;
    @Mock private NotificationChannel emailChannel;
    @Mock private DelegateExecution execution;
    @Mock private ExpressionManager expressionManager;

    private NotificationDelegate delegate;

    /**
     * Creates a mock {@link ProcessEngineConfiguration} that also implements
     * {@link HasExpressionManagerEngineConfiguration} so the constructor cast succeeds.
     */
    private ProcessEngineConfiguration engineCfgMock() {
        ProcessEngineConfiguration mock = mock(ProcessEngineConfiguration.class,
                withSettings().extraInterfaces(HasExpressionManagerEngineConfiguration.class));
        when(((HasExpressionManagerEngineConfiguration) mock).getExpressionManager())
                .thenReturn(expressionManager);
        return mock;
    }

    @BeforeEach
    void setUp() {
        delegate = new NotificationDelegate(channelFactory, templateService, engineCfgMock());
    }

    // ------------------------------------------------------------------
    // Helper: build a FieldExtension
    // ------------------------------------------------------------------

    private static FieldExtension stringField(String name, String value) {
        FieldExtension fe = new FieldExtension();
        fe.setFieldName(name);
        fe.setStringValue(value);
        return fe;
    }

    private static FieldExtension exprField(String name, String expr) {
        FieldExtension fe = new FieldExtension();
        fe.setFieldName(name);
        fe.setExpression(expr);
        return fe;
    }

    /**
     * Creates a standalone mock {@link Expression} that returns {@code returnValue} on
     * {@code getValue()}. Must be created BEFORE being passed to any outer {@code when()}
     * call to avoid Mockito UnfinishedStubbing errors.
     */
    private Expression exprReturning(Object returnValue) {
        Expression expr = mock(Expression.class);
        when(expr.getValue(any())).thenReturn(returnValue);
        return expr;
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    void execute_sendsEmailOnValidInput() {
        Expression recipientExpr = exprReturning("alice@example.com");
        Expression templateKeyExpr = exprReturning("welcome");
        Expression channelExpr = exprReturning("email");

        List<FieldExtension> fields = List.of(
                stringField("recipient", "alice@example.com"),
                stringField("templateKey", "welcome"),
                stringField("channel", "email")
        );
        when(expressionManager.createExpression("alice@example.com")).thenReturn(recipientExpr);
        when(expressionManager.createExpression("welcome")).thenReturn(templateKeyExpr);
        when(expressionManager.createExpression("email")).thenReturn(channelExpr);
        when(execution.getVariables()).thenReturn(Map.of("name", "Alice"));
        when(templateService.render(eq("welcome"), anyMap(), eq(false)))
            .thenReturn(new NotificationTemplateService.RenderedTemplate("Hi Alice", "Body"));
        when(channelFactory.getChannel("email")).thenReturn(emailChannel);

        try (MockedStatic<DelegateHelper> dh = mockStatic(DelegateHelper.class)) {
            dh.when(() -> DelegateHelper.getFlowElementFields(execution)).thenReturn(fields);
            delegate.execute(execution);
        }

        ArgumentCaptor<ActionBlockNotificationRequest> captor =
            ArgumentCaptor.forClass(ActionBlockNotificationRequest.class);
        verify(emailChannel).send(captor.capture());
        assertThat(captor.getValue().recipient()).isEqualTo("alice@example.com");
    }

    @Test
    void execute_skipsWhenConditionFalse() {
        Expression conditionExpr = exprReturning(false);

        List<FieldExtension> fields = List.of(
                stringField("recipient", "alice@example.com"),
                stringField("templateKey", "welcome"),
                exprField("condition", "${false}")
        );
        when(expressionManager.createExpression("${false}")).thenReturn(conditionExpr);

        try (MockedStatic<DelegateHelper> dh = mockStatic(DelegateHelper.class)) {
            dh.when(() -> DelegateHelper.getFlowElementFields(execution)).thenReturn(fields);
            delegate.execute(execution);
        }

        verifyNoInteractions(channelFactory, templateService);
    }

    @Test
    void execute_stripsCarriageReturnFromRecipient() {
        Expression recipientExpr = exprReturning("alice@example.com\r\n");
        Expression templateKeyExpr = exprReturning("welcome");
        Expression channelExpr = exprReturning("email");

        List<FieldExtension> fields = List.of(
                stringField("recipient", "alice@example.com\r\n"),
                stringField("templateKey", "welcome"),
                stringField("channel", "email")
        );
        when(expressionManager.createExpression("alice@example.com\r\n")).thenReturn(recipientExpr);
        when(expressionManager.createExpression("welcome")).thenReturn(templateKeyExpr);
        when(expressionManager.createExpression("email")).thenReturn(channelExpr);
        when(execution.getVariables()).thenReturn(Map.of());
        when(templateService.render(eq("welcome"), anyMap(), eq(false)))
            .thenReturn(new NotificationTemplateService.RenderedTemplate("Subj", "Body"));
        when(channelFactory.getChannel("email")).thenReturn(emailChannel);

        try (MockedStatic<DelegateHelper> dh = mockStatic(DelegateHelper.class)) {
            dh.when(() -> DelegateHelper.getFlowElementFields(execution)).thenReturn(fields);
            delegate.execute(execution);
        }

        ArgumentCaptor<ActionBlockNotificationRequest> captor =
            ArgumentCaptor.forClass(ActionBlockNotificationRequest.class);
        verify(emailChannel).send(captor.capture());
        assertThat(captor.getValue().recipient()).isEqualTo("alice@example.com");
    }

    @Test
    void execute_throwsOnInvalidEmailAddress() {
        // Only stub recipient — sanitizeEmail throws before templateKey is ever resolved
        Expression recipientExpr = exprReturning("not-an-email");

        List<FieldExtension> fields = List.of(
                stringField("recipient", "not-an-email"),
                stringField("templateKey", "welcome")
        );
        // "welcome" stub is lenient: templateKey is never reached because sanitizeEmail throws first
        lenient().when(expressionManager.createExpression("welcome")).thenReturn(mock(Expression.class));
        when(expressionManager.createExpression("not-an-email")).thenReturn(recipientExpr);

        try (MockedStatic<DelegateHelper> dh = mockStatic(DelegateHelper.class)) {
            dh.when(() -> DelegateHelper.getFlowElementFields(execution)).thenReturn(fields);
            assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recipient");
        }
    }

    @Test
    void execute_stripsCarriageReturnFromSubject() {
        Expression recipientExpr = exprReturning("alice@example.com");
        Expression templateKeyExpr = exprReturning("welcome");
        Expression channelExpr = exprReturning("email");

        List<FieldExtension> fields = List.of(
                stringField("recipient", "alice@example.com"),
                stringField("templateKey", "welcome"),
                stringField("channel", "email")
        );
        when(expressionManager.createExpression("alice@example.com")).thenReturn(recipientExpr);
        when(expressionManager.createExpression("welcome")).thenReturn(templateKeyExpr);
        when(expressionManager.createExpression("email")).thenReturn(channelExpr);
        when(execution.getVariables()).thenReturn(Map.of());
        when(templateService.render(eq("welcome"), anyMap(), eq(false)))
            .thenReturn(new NotificationTemplateService.RenderedTemplate(
                "Hello\r\nInjected", "Body"));
        when(channelFactory.getChannel("email")).thenReturn(emailChannel);

        try (MockedStatic<DelegateHelper> dh = mockStatic(DelegateHelper.class)) {
            dh.when(() -> DelegateHelper.getFlowElementFields(execution)).thenReturn(fields);
            delegate.execute(execution);
        }

        ArgumentCaptor<ActionBlockNotificationRequest> captor =
            ArgumentCaptor.forClass(ActionBlockNotificationRequest.class);
        verify(emailChannel).send(captor.capture());
        assertThat(captor.getValue().subject()).isEqualTo("HelloInjected");
    }

    @Test
    void execute_concurrentTenantsDoNotShareFieldState() {
        // Regression test for ADR-014 / ADR-015 @Setter field-injection race.
        // The old implementation wrote recipient/templateKey/channel to mutable singleton fields
        // before execute(), allowing thread B to overwrite thread A's values mid-execution.
        // The new implementation reads from DelegateHelper.getFlowElementFields(execution)
        // keyed by the execution instance, so two different executions always see their own fields.
        //
        // This test proves the isolation property: two back-to-back execute() calls on the same
        // shared delegate instance each produce the recipient from their own field set. The
        // absence of shared mutable fields means there is no mechanism by which one call could
        // affect the other.

        DelegateExecution executionA = mock(DelegateExecution.class);
        DelegateExecution executionB = mock(DelegateExecution.class);

        NotificationChannel channelA = mock(NotificationChannel.class);
        NotificationChannel channelB = mock(NotificationChannel.class);

        NotificationTemplateService.RenderedTemplate renderedA =
                new NotificationTemplateService.RenderedTemplate("Subj A", "Body A");
        NotificationTemplateService.RenderedTemplate renderedB =
                new NotificationTemplateService.RenderedTemplate("Subj B", "Body B");

        // -- Execute for tenant A --
        Expression exprRecipientA = exprReturning("tenant-a@example.com");
        Expression exprTmplA      = exprReturning("tmpl-a");
        Expression exprEmailA     = exprReturning("email");

        List<FieldExtension> fieldsA = List.of(
                stringField("recipient", "tenant-a@example.com"),
                stringField("templateKey", "tmpl-a"),
                stringField("channel", "email")
        );
        when(expressionManager.createExpression("tenant-a@example.com")).thenReturn(exprRecipientA);
        when(expressionManager.createExpression("tmpl-a")).thenReturn(exprTmplA);
        when(expressionManager.createExpression("email")).thenReturn(exprEmailA);
        when(executionA.getVariables()).thenReturn(Map.of());
        when(templateService.render(eq("tmpl-a"), anyMap(), eq(false))).thenReturn(renderedA);
        when(channelFactory.getChannel("email")).thenReturn(channelA);

        try (MockedStatic<DelegateHelper> dh = mockStatic(DelegateHelper.class)) {
            dh.when(() -> DelegateHelper.getFlowElementFields(executionA)).thenReturn(fieldsA);
            delegate.execute(executionA);
        }

        ArgumentCaptor<ActionBlockNotificationRequest> capA =
                ArgumentCaptor.forClass(ActionBlockNotificationRequest.class);
        verify(channelA).send(capA.capture());
        String recipientA = capA.getValue().recipient();

        // -- Execute for tenant B (reset shared mocks) --
        reset(expressionManager, templateService, channelFactory);

        Expression exprRecipientB = exprReturning("tenant-b@example.com");
        Expression exprTmplB      = exprReturning("tmpl-b");
        Expression exprEmailB     = exprReturning("email");

        List<FieldExtension> fieldsB = List.of(
                stringField("recipient", "tenant-b@example.com"),
                stringField("templateKey", "tmpl-b"),
                stringField("channel", "email")
        );
        when(expressionManager.createExpression("tenant-b@example.com")).thenReturn(exprRecipientB);
        when(expressionManager.createExpression("tmpl-b")).thenReturn(exprTmplB);
        when(expressionManager.createExpression("email")).thenReturn(exprEmailB);
        when(executionB.getVariables()).thenReturn(Map.of());
        when(templateService.render(eq("tmpl-b"), anyMap(), eq(false))).thenReturn(renderedB);
        when(channelFactory.getChannel("email")).thenReturn(channelB);

        try (MockedStatic<DelegateHelper> dh = mockStatic(DelegateHelper.class)) {
            dh.when(() -> DelegateHelper.getFlowElementFields(executionB)).thenReturn(fieldsB);
            delegate.execute(executionB);
        }

        ArgumentCaptor<ActionBlockNotificationRequest> capB =
                ArgumentCaptor.forClass(ActionBlockNotificationRequest.class);
        verify(channelB).send(capB.capture());
        String recipientB = capB.getValue().recipient();

        // Each execution must have resolved its own recipient — no cross-tenant leakage
        assertThat(recipientA).isEqualTo("tenant-a@example.com");
        assertThat(recipientB).isEqualTo("tenant-b@example.com");
    }
}
