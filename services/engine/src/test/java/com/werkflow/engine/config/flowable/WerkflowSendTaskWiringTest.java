package com.werkflow.engine.config.flowable;

import org.flowable.bpmn.constants.BpmnXMLConstants;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.FieldExtension;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SendTask;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.common.engine.impl.el.ExpressionManager;
import org.flowable.engine.impl.bpmn.behavior.ServiceTaskDelegateExpressionActivityBehavior;
import org.flowable.engine.impl.bpmn.parser.BpmnParse;
import org.flowable.engine.impl.bpmn.parser.factory.DefaultActivityBehaviorFactory;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.validation.ValidationError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit-level wiring tests for {@link WerkflowSendTaskParseHandler} and
 * {@link WerkflowSendTaskValidator}.
 *
 * <p>Uses Mockito (no Testcontainers / Spring context) to verify that:
 * <ol>
 *   <li>The parse handler sets a {@link ServiceTaskDelegateExpressionActivityBehavior} on a
 *       SendTask configured with a {@code flowable:delegateExpression} extension attribute,
 *       which is how the real BPMN parser stores the attribute (since
 *       {@code SendTaskXMLConverter} does not map it to typed model fields).</li>
 *   <li>The validator produces zero errors for that same configuration.</li>
 * </ol>
 *
 * @see WerkflowSendTaskParseHandler
 * @see WerkflowSendTaskValidator
 */
@ExtendWith(MockitoExtension.class)
class WerkflowSendTaskWiringTest {

    @Mock private BpmnParse bpmnParse;
    @Mock private DefaultActivityBehaviorFactory behaviorFactory;
    @Mock private ExpressionManager expressionManager;
    @Mock private ProcessEngineConfigurationImpl engineConfig;

    // ------------------------------------------------------------------
    // Helper — builds a SendTask as the BPMN converter actually produces it:
    // flowable:delegateExpression is stored as an ExtensionAttribute, NOT
    // in implementationType (SendTaskXMLConverter doesn't read that attribute).
    // ------------------------------------------------------------------

    private SendTask delegateExpressionSendTask() {
        SendTask sendTask = new SendTask();
        sendTask.setId("notify-task");

        // Simulate how the BPMN converter stores the flowable:delegateExpression attribute
        ExtensionAttribute attr = new ExtensionAttribute();
        attr.setNamespace(BpmnXMLConstants.FLOWABLE_EXTENSIONS_NAMESPACE);
        attr.setNamespacePrefix(BpmnXMLConstants.FLOWABLE_EXTENSIONS_PREFIX);
        attr.setName("delegateExpression");
        attr.setValue("${notificationDelegate}");
        sendTask.addAttribute(attr);

        FieldExtension recipientField = new FieldExtension();
        recipientField.setFieldName("recipient");
        recipientField.setStringValue("test@example.com");
        sendTask.getFieldExtensions().add(recipientField);

        return sendTask;
    }

    // ------------------------------------------------------------------
    // Parse handler test
    // ------------------------------------------------------------------

    @Test
    void parseHandler_setsBehavior_whenSendTaskHasDelegateExpression() {
        SendTask sendTask = delegateExpressionSendTask();

        Expression mockExpression = mock(Expression.class);
        when(bpmnParse.getActivityBehaviorFactory()).thenReturn(behaviorFactory);
        when(behaviorFactory.createFieldDeclarations(any())).thenReturn(List.of());
        when(expressionManager.createExpression(anyString())).thenReturn(mockExpression);
        when(engineConfig.getExpressionManager()).thenReturn(expressionManager);

        WerkflowSendTaskParseHandler handler = new WerkflowSendTaskParseHandler();

        try (MockedStatic<CommandContextUtil> ccu = mockStatic(CommandContextUtil.class)) {
            ccu.when(CommandContextUtil::getProcessEngineConfiguration).thenReturn(engineConfig);

            // super.executeParse() will enter the native handler's else branch (WARN + no behavior).
            // Our override then detects the delegateExpression attribute and wires the behavior.
            handler.executeParse(bpmnParse, sendTask);
        }

        assertThat(sendTask.getBehavior())
                .isNotNull()
                .isInstanceOf(ServiceTaskDelegateExpressionActivityBehavior.class);
    }

    // ------------------------------------------------------------------
    // Validator test
    // ------------------------------------------------------------------

    @Test
    void validator_acceptsSendTaskWithDelegateExpression() {
        SendTask sendTask = delegateExpressionSendTask();

        BpmnModel bpmnModel = new BpmnModel();
        Process process = new Process();
        process.setId("test-process");
        process.addFlowElement(sendTask);

        WerkflowSendTaskValidator validator = new WerkflowSendTaskValidator();
        List<ValidationError> errors = new ArrayList<>();

        validator.executeValidation(bpmnModel, process, errors);

        assertThat(errors).isEmpty();
    }
}
