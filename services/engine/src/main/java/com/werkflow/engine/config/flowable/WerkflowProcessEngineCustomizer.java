package com.werkflow.engine.config.flowable;

import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.validation.ProcessValidatorFactory;
import org.flowable.validation.ProcessValidatorImpl;
import org.flowable.validation.validator.impl.SendTaskValidator;

import java.util.List;

/**
 * Applies the Werkflow-specific BPMN customizations — custom parse handlers and the
 * pre-deployment validator family — to a Flowable process-engine configuration.
 *
 * <p>Extracted so the production engine ({@code FlowableConfig}) and the in-memory test engine
 * ({@code WerkflowTestProcessEngine}) share ONE definition of these customizations. This keeps BPMN
 * unit tests faithful to production deploy-time behaviour: the same parse handlers and the same
 * validator gate (SendTask / ScriptTask / BusinessRuleTask / ManualTask) run in both.
 *
 * <p>Spring-only concerns stay in {@code FlowableConfig} and are intentionally NOT applied here:
 * the {@code RestrictedExpressionManager} (needs the Spring beans map) and the global
 * task-notification listener (an email side-effect bean).
 *
 * <p>Validator registration order within each validator set:
 * <ol>
 *   <li>{@link WerkflowSendTaskValidator} — relaxed SendTask rules (replaces Flowable default).</li>
 *   <li>{@link WerkflowScriptTaskQuarantineValidator} — rejects all script tasks (ADR-016).</li>
 *   <li>{@link WerkflowBusinessRuleTaskValidator} — rejects businessRuleTask dead-config (ADR-026).</li>
 *   <li>{@link WerkflowManualTaskValidator} — rejects confirmationRequired on manualTask (ADR-017).</li>
 *   <li>{@link WerkflowLinkEventValidator} — rejects link events unsupported in Flowable 7.2.</li>
 *   <li>{@link WerkflowDeadExtensionAttrValidator} — rejects F-EV-2 dead flowable:* attrs (ADR-009).</li>
 * </ol>
 */
public final class WerkflowProcessEngineCustomizer {

    private WerkflowProcessEngineCustomizer() {
    }

    /**
     * Registers the custom SendTask parse handler and replaces the default validator set with the
     * Werkflow validator family (relaxed SendTask + ScriptTask/BusinessRuleTask/ManualTask rejecters).
     */
    public static void applyValidatorsAndParseHandlers(ProcessEngineConfigurationImpl configuration) {
        configuration.setCustomDefaultBpmnParseHandlers(List.of(new WerkflowSendTaskParseHandler()));

        ProcessValidatorImpl processValidator =
                (ProcessValidatorImpl) new ProcessValidatorFactory().createDefaultProcessValidator();
        processValidator.getValidatorSets().forEach(set -> {
            set.removeValidator(SendTaskValidator.class);
            set.addValidator(new WerkflowSendTaskValidator());
            set.addValidator(new WerkflowScriptTaskQuarantineValidator());
            set.addValidator(new WerkflowBusinessRuleTaskValidator());
            set.addValidator(new WerkflowManualTaskValidator());
            set.addValidator(new WerkflowLinkEventValidator());
            set.addValidator(new WerkflowDeadExtensionAttrValidator());
        });
        configuration.setProcessValidator(processValidator);
    }
}
