package com.werkflow.engine.config.flowable;

import org.apache.commons.lang3.StringUtils;
import org.flowable.bpmn.constants.BpmnXMLConstants;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ImplementationType;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SendTask;
import org.flowable.validation.ValidationError;
import org.flowable.validation.validator.Problems;
import org.flowable.validation.validator.impl.SendTaskValidator;

import java.util.List;

/**
 * Extended validator for {@code <bpmn:sendTask>} that accepts
 * {@code flowable:class}, {@code flowable:delegateExpression}, and
 * {@code flowable:expression} as valid implementation bindings in addition to the
 * Flowable defaults ({@code mail}, {@code camel}, {@code ##WebService}).
 *
 * <p>The parent {@link SendTaskValidator} treats any sendTask without
 * {@code flowable:type=mail|camel} or {@code implementationType=##WebService} as invalid
 * ({@code SEND_TASK_INVALID_IMPLEMENTATION}). This override relaxes that gate
 * to also accept the three custom implementation bindings read from the Flowable
 * extensions namespace (since {@link org.flowable.bpmn.converter.SendTaskXMLConverter}
 * does not map them to typed model fields the way {@code ServiceTaskXMLConverter} does).
 *
 * <p>All other parent checks are preserved: {@code validateFieldDeclarationsForEmail}
 * for {@code type=mail}, and {@code verifyWebservice} for the web service case.
 *
 * @see <a href="../../../../../../../../../../docs/adr/ADR-015-werkflow-custom-sendtask-parse-handler.md">ADR-015</a>
 */
public class WerkflowSendTaskValidator extends SendTaskValidator {

    private static final String ATTR_DELEGATE_EXPRESSION = "delegateExpression";
    private static final String ATTR_CLASS = "class";
    private static final String ATTR_EXPRESSION = "expression";

    @Override
    protected void executeValidation(BpmnModel bpmnModel, Process process, List<ValidationError> errors) {
        List<SendTask> sendTasks = process.findFlowElementsOfType(SendTask.class);
        for (SendTask sendTask : sendTasks) {

            // Verify implementation — accept native types AND our custom delegate bindings
            if (!isValidImplementation(sendTask)) {
                addError(errors, Problems.SEND_TASK_INVALID_IMPLEMENTATION, process, sendTask,
                        "One of the attributes 'type', 'operation', 'class', 'delegateExpression', "
                                + "or 'expression' is mandatory on sendTask");
            }

            // Verify type (mail / camel only — same as parent)
            if (StringUtils.isNotEmpty(sendTask.getType())) {
                if (!"mail".equalsIgnoreCase(sendTask.getType())
                        && !"camel".equalsIgnoreCase(sendTask.getType())) {
                    addError(errors, Problems.SEND_TASK_INVALID_TYPE, process, sendTask,
                            "Invalid or unsupported type for send task");
                }
                if ("mail".equalsIgnoreCase(sendTask.getType())) {
                    validateFieldDeclarationsForEmail(process, sendTask,
                            sendTask.getFieldExtensions(), errors);
                }
            }

            // Web service
            verifyWebservice(bpmnModel, process, sendTask, errors);
        }
    }

    /**
     * Returns {@code true} when the sendTask has a valid implementation, including:
     * <ul>
     *   <li>Non-empty {@code flowable:type} (mail / camel are further checked in the type block)</li>
     *   <li>{@code ##WebService} implementation type (set by {@code SendTaskXMLConverter})</li>
     *   <li>Non-empty {@code flowable:class}, {@code flowable:delegateExpression}, or
     *       {@code flowable:expression} extension attribute</li>
     * </ul>
     *
     * <p>Note: {@code SendTaskXMLConverter} only sets {@code implementationType} for the
     * {@code ##WebService} case. For custom delegate bindings, the attributes are stored in
     * {@code BaseElement.attributes} under the Flowable extensions namespace.
     */
    private boolean isValidImplementation(SendTask sendTask) {
        if (StringUtils.isNotEmpty(sendTask.getType())) {
            return true;
        }
        if (ImplementationType.IMPLEMENTATION_TYPE_WEBSERVICE.equalsIgnoreCase(
                sendTask.getImplementationType())) {
            return true;
        }
        return hasCustomDelegateAttribute(sendTask);
    }

    private boolean hasCustomDelegateAttribute(SendTask sendTask) {
        String ns = BpmnXMLConstants.FLOWABLE_EXTENSIONS_NAMESPACE;
        return StringUtils.isNotEmpty(sendTask.getAttributeValue(ns, ATTR_DELEGATE_EXPRESSION))
                || StringUtils.isNotEmpty(sendTask.getAttributeValue(ns, ATTR_CLASS))
                || StringUtils.isNotEmpty(sendTask.getAttributeValue(ns, ATTR_EXPRESSION));
    }
}
