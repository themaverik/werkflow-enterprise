package com.werkflow.engine.config.flowable;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.ScriptTask;
import org.flowable.validation.ValidationError;
import org.flowable.validation.validator.ProcessLevelValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Hard-rejects any {@code <bpmn:scriptTask>} element encountered during BPMN deployment.
 *
 * <p>Script tasks pose an unmitigated RCE risk against the engine JVM until the full
 * Werkflow Script Task Security Model (ADR-016) is in place — admin enforcement,
 * Groovy {@code SecureASTCustomizer} sandbox, and audit logging. Until then this
 * validator aborts deployment immediately with a clear, actionable error.
 *
 * <p>No configuration flag is provided to bypass this quarantine; that is intentional.
 * Zero BPMN deployments currently use {@code scriptTask}, so no live process breaks.
 *
 * @see <a href="../../../../../../../../../../docs/adr/ADR-016-werkflow-script-task-security-model.md">ADR-016</a>
 */
public class WerkflowScriptTaskQuarantineValidator extends ProcessLevelValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(WerkflowScriptTaskQuarantineValidator.class);

    /** Stable error code emitted for every quarantined script task element. */
    public static final String WERKFLOW_SCRIPT_TASK_QUARANTINED = "WERKFLOW_SCRIPT_TASK_QUARANTINED";

    @Override
    protected void executeValidation(BpmnModel bpmnModel, Process process, List<ValidationError> errors) {
        List<ScriptTask> scriptTasks = process.findFlowElementsOfType(ScriptTask.class);
        for (ScriptTask scriptTask : scriptTasks) {
            String id = scriptTask.getId();
            String name = scriptTask.getName();
            String scriptFormat = scriptTask.getScriptFormat();

            LOGGER.warn("WerkflowScriptTaskQuarantineValidator: rejecting scriptTask id='{}' name='{}' "
                    + "scriptFormat='{}' in process '{}' — quarantined under ADR-016",
                    id, name, scriptFormat, process.getId());

            String message = String.format(
                    "Script tasks are quarantined under the Werkflow security model (ADR-016). "
                            + "Element id=%s, name='%s', scriptFormat='%s'. "
                            + "Contact platform admin to enable script execution.",
                    id, name, scriptFormat);

            addError(errors, WERKFLOW_SCRIPT_TASK_QUARANTINED, process, scriptTask, message);
        }
    }
}
