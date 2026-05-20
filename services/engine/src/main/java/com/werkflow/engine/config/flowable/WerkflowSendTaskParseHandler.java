package com.werkflow.engine.config.flowable;

import org.apache.commons.lang3.StringUtils;
import org.flowable.bpmn.constants.BpmnXMLConstants;
import org.flowable.bpmn.model.ImplementationType;
import org.flowable.bpmn.model.SendTask;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.common.engine.impl.el.ExpressionManager;
import org.flowable.engine.impl.bpmn.behavior.ServiceTaskDelegateExpressionActivityBehavior;
import org.flowable.engine.impl.bpmn.parser.BpmnParse;
import org.flowable.engine.impl.bpmn.parser.FieldDeclaration;
import org.flowable.engine.impl.bpmn.parser.factory.AbstractBehaviorFactory;
import org.flowable.engine.impl.bpmn.parser.handler.SendTaskParseHandler;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Custom Flowable parse handler that extends {@link SendTaskParseHandler} to support
 * {@code flowable:class}, {@code flowable:delegateExpression}, and {@code flowable:expression}
 * bindings on {@code <bpmn:sendTask>} elements.
 *
 * <p>Flowable's default {@code SendTaskParseHandler} only wires behaviors for
 * {@code flowable:type=mail|camel|dmn} and {@code ##WebService} implementations.
 * Any other binding is silently ignored (no behavior set, logged as WARN at runtime).
 * This handler calls {@code super.executeParse()} first to preserve all native handling,
 * then reads {@code flowable:*} extension attributes directly from the BPMN model's
 * attribute map to wire the remaining implementation types when the behavior is still unset.
 *
 * <p>Note: {@link org.flowable.bpmn.converter.SendTaskXMLConverter} does not populate
 * {@code SendTask.implementationType} for {@code flowable:delegateExpression} the way
 * {@code ServiceTaskXMLConverter} does for {@code ServiceTask}. The attribute value is
 * instead stored in {@code BaseElement.attributes} under the Flowable extensions namespace.
 * This handler reads it from there.
 *
 * <p>This enables the SEND_NOTIFICATION action block (bound as
 * {@code flowable:delegateExpression="${notificationDelegate}"}) to deploy and execute
 * correctly while preserving the BPMN envelope-icon semantic of {@code bpmn:sendTask}.
 *
 * @see <a href="../../../../../../../../../../docs/flowable-7.2/Send-Task.md">Send-Task.md §6 D-ST-1</a>
 * @see <a href="../../../../../../../../../../docs/adr/ADR-015-werkflow-custom-sendtask-parse-handler.md">ADR-015</a>
 */
public class WerkflowSendTaskParseHandler extends SendTaskParseHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(WerkflowSendTaskParseHandler.class);

    private static final String ATTR_DELEGATE_EXPRESSION = "delegateExpression";
    private static final String ATTR_CLASS = "class";
    private static final String ATTR_EXPRESSION = "expression";

    @Override
    protected void executeParse(BpmnParse bpmnParse, SendTask sendTask) {
        // Preserve all native handling: mail, camel, dmn, ##WebService
        super.executeParse(bpmnParse, sendTask);

        // Only proceed if native handling left the behavior unset
        if (sendTask.getBehavior() != null) {
            return;
        }

        // SendTaskXMLConverter does not read flowable:class / delegateExpression / expression
        // into model fields (unlike ServiceTaskXMLConverter). Read directly from extension attrs.
        String delegateExpr = sendTask.getAttributeValue(
                BpmnXMLConstants.FLOWABLE_EXTENSIONS_NAMESPACE, ATTR_DELEGATE_EXPRESSION);
        String classValue = sendTask.getAttributeValue(
                BpmnXMLConstants.FLOWABLE_EXTENSIONS_NAMESPACE, ATTR_CLASS);
        String expression = sendTask.getAttributeValue(
                BpmnXMLConstants.FLOWABLE_EXTENSIONS_NAMESPACE, ATTR_EXPRESSION);

        ExpressionManager expressionManager = CommandContextUtil.getProcessEngineConfiguration()
                .getExpressionManager();
        AbstractBehaviorFactory factory = (AbstractBehaviorFactory) bpmnParse.getActivityBehaviorFactory();
        List<FieldDeclaration> fieldDeclarations = factory.createFieldDeclarations(
                sendTask.getFieldExtensions());

        if (StringUtils.isNotEmpty(delegateExpr)) {
            Expression delegateExpression = expressionManager.createExpression(delegateExpr);
            sendTask.setBehavior(new ServiceTaskDelegateExpressionActivityBehavior(
                    sendTask.getId(), delegateExpression, null, fieldDeclarations,
                    sendTask.getMapExceptions(), false));
            LOGGER.debug("WerkflowSendTaskParseHandler: wired delegateExpression='{}' on sendTask '{}'",
                    delegateExpr, sendTask.getId());

        } else if (StringUtils.isNotEmpty(expression)) {
            // ServiceTaskExpressionActivityBehavior and createServiceTaskExpressionActivityBehavior
            // both require a ServiceTask argument — use a proxy the same way as the class branch.
            ServiceTask proxy = buildServiceTaskProxy(sendTask, expression,
                    ImplementationType.IMPLEMENTATION_TYPE_EXPRESSION);
            sendTask.setBehavior(bpmnParse.getActivityBehaviorFactory()
                    .createServiceTaskExpressionActivityBehavior(proxy));
            LOGGER.debug("WerkflowSendTaskParseHandler: wired expression='{}' on sendTask '{}'",
                    expression, sendTask.getId());

        } else if (StringUtils.isNotEmpty(classValue)) {
            // createClassDelegateServiceTask requires a ServiceTask — build a minimal proxy
            ServiceTask proxy = buildServiceTaskProxy(sendTask, classValue,
                    ImplementationType.IMPLEMENTATION_TYPE_CLASS);
            sendTask.setBehavior(bpmnParse.getActivityBehaviorFactory().createClassDelegateServiceTask(proxy));
            LOGGER.debug("WerkflowSendTaskParseHandler: wired class='{}' on sendTask '{}'",
                    classValue, sendTask.getId());

        } else {
            LOGGER.warn("WerkflowSendTaskParseHandler: sendTask '{}' has no resolvable implementation "
                    + "(type was empty, operationRef resolved to no behavior) — no behavior wired",
                    sendTask.getId());
        }
    }

    /**
     * Builds a minimal {@link ServiceTask} proxy so factory methods that require a {@code ServiceTask}
     * ({@code createClassDelegateServiceTask}, {@code createServiceTaskExpressionActivityBehavior}) can
     * be called for a {@code SendTask}. Only the fields consumed by those factory methods are populated.
     */
    private ServiceTask buildServiceTaskProxy(SendTask sendTask, String implementation, String implType) {
        ServiceTask proxy = new ServiceTask();
        proxy.setId(sendTask.getId());
        proxy.setImplementation(implementation);
        proxy.setImplementationType(implType);
        proxy.getFieldExtensions().addAll(sendTask.getFieldExtensions());
        proxy.getMapExceptions().addAll(sendTask.getMapExceptions());
        return proxy;
    }
}
