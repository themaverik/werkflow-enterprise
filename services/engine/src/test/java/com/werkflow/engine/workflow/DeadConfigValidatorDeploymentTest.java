package com.werkflow.engine.workflow;

import com.werkflow.engine.config.flowable.WerkflowBusinessRuleTaskValidator;
import com.werkflow.engine.config.flowable.WerkflowManualTaskValidator;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.flowable.validation.ProcessValidatorFactory;
import org.flowable.validation.ProcessValidatorImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the two dead-config validators ({@link WerkflowBusinessRuleTaskValidator},
 * {@link WerkflowManualTaskValidator}) reject the targeted constructs at DEPLOY time, against the
 * real Flowable 7.2 BPMN parser — not a hand-built model. This is the faithful test of the
 * universal pre-deployment gate: it registers the validators exactly as {@code FlowableConfig} does
 * and deploys real XML, so it also confirms the {@code flowable:field confirmationRequired} survives
 * parse onto the {@code manualTask} model (the one representation uncertainty in D-MT-4).
 *
 * <p>Standalone in-mem engine (mirrors {@link SignalTenantIsolationTest}); no Spring/Postgres/Vault.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Dead-config validators reject at deploy time (real parser)")
class DeadConfigValidatorDeploymentTest {

    private static final String BRT_PROCESS = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:flowable="http://flowable.org/bpmn"
                     targetNamespace="http://werkflow.com/bpmn/test">
          <process id="brt-proc" isExecutable="true">
            <startEvent id="s"/>
            <sequenceFlow id="f1" sourceRef="s" targetRef="b"/>
            <businessRuleTask id="b" name="Evaluate" flowable:decisionRef="some_decision"/>
            <sequenceFlow id="f2" sourceRef="b" targetRef="e"/>
            <endEvent id="e"/>
          </process>
        </definitions>
        """;

    /** manualTask declaring confirmationRequired via a nested flowable:string child. */
    private static final String MANUAL_CONFIRM_STRING = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:flowable="http://flowable.org/bpmn"
                     targetNamespace="http://werkflow.com/bpmn/test">
          <process id="manual-confirm-string" isExecutable="true">
            <startEvent id="s"/>
            <sequenceFlow id="f1" sourceRef="s" targetRef="m"/>
            <manualTask id="m" name="Confirm">
              <extensionElements>
                <flowable:field name="confirmationRequired">
                  <flowable:string>true</flowable:string>
                </flowable:field>
              </extensionElements>
            </manualTask>
            <sequenceFlow id="f2" sourceRef="m" targetRef="e"/>
            <endEvent id="e"/>
          </process>
        </definitions>
        """;

    /** manualTask declaring confirmationRequired via the value attribute. */
    private static final String MANUAL_CONFIRM_ATTR = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:flowable="http://flowable.org/bpmn"
                     targetNamespace="http://werkflow.com/bpmn/test">
          <process id="manual-confirm-attr" isExecutable="true">
            <startEvent id="s"/>
            <sequenceFlow id="f1" sourceRef="s" targetRef="m"/>
            <manualTask id="m" name="Confirm">
              <extensionElements>
                <flowable:field name="confirmationRequired" value="true"/>
              </extensionElements>
            </manualTask>
            <sequenceFlow id="f2" sourceRef="m" targetRef="e"/>
            <endEvent id="e"/>
          </process>
        </definitions>
        """;

    /** manualTask declaring confirmationRequired via a direct text node (non-standard hand form). */
    private static final String MANUAL_CONFIRM_DIRECT_TEXT = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:flowable="http://flowable.org/bpmn"
                     targetNamespace="http://werkflow.com/bpmn/test">
          <process id="manual-confirm-text" isExecutable="true">
            <startEvent id="s"/>
            <sequenceFlow id="f1" sourceRef="s" targetRef="m"/>
            <manualTask id="m" name="Confirm">
              <extensionElements>
                <flowable:field name="confirmationRequired">true</flowable:field>
              </extensionElements>
            </manualTask>
            <sequenceFlow id="f2" sourceRef="m" targetRef="e"/>
            <endEvent id="e"/>
          </process>
        </definitions>
        """;

    /** Plain pass-through manualTask — must deploy cleanly. */
    private static final String MANUAL_PLAIN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     targetNamespace="http://werkflow.com/bpmn/test">
          <process id="manual-plain" isExecutable="true">
            <startEvent id="s"/>
            <sequenceFlow id="f1" sourceRef="s" targetRef="m"/>
            <manualTask id="m" name="Note"/>
            <sequenceFlow id="f2" sourceRef="m" targetRef="e"/>
            <endEvent id="e"/>
          </process>
        </definitions>
        """;

    private ProcessEngine processEngine;
    private RepositoryService repositoryService;

    @BeforeAll
    void bootEngine() {
        StandaloneInMemProcessEngineConfiguration cfg = new StandaloneInMemProcessEngineConfiguration();
        cfg.setJdbcUrl("jdbc:h2:mem:deadConfigValidators;DB_CLOSE_DELAY=1000");
        cfg.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);

        // Register the two validators exactly as FlowableConfig does in production.
        ProcessValidatorImpl processValidator =
                (ProcessValidatorImpl) new ProcessValidatorFactory().createDefaultProcessValidator();
        processValidator.getValidatorSets().forEach(set -> {
            set.addValidator(new WerkflowBusinessRuleTaskValidator());
            set.addValidator(new WerkflowManualTaskValidator());
        });
        cfg.setProcessValidator(processValidator);

        processEngine = cfg.buildProcessEngine();
        repositoryService = processEngine.getRepositoryService();
    }

    @AfterAll
    void shutdown() {
        if (processEngine != null) {
            processEngine.close();
        }
    }

    private void deploy(String name, String xml) {
        repositoryService.createDeployment().addString(name + ".bpmn20.xml", xml).name(name).deploy();
    }

    @Test
    @DisplayName("businessRuleTask is rejected at deploy with WERKFLOW_BUSINESS_RULE_TASK_UNSUPPORTED")
    void businessRuleTask_rejected() {
        assertThatThrownBy(() -> deploy("brt", BRT_PROCESS))
                .hasMessageContaining(WerkflowBusinessRuleTaskValidator.WERKFLOW_BUSINESS_RULE_TASK_UNSUPPORTED);
    }

    @Test
    @DisplayName("manualTask + confirmationRequired (nested string) is rejected at deploy")
    void manualTaskConfirmString_rejected() {
        assertThatThrownBy(() -> deploy("manual-confirm-string", MANUAL_CONFIRM_STRING))
                .hasMessageContaining(WerkflowManualTaskValidator.WERKFLOW_MANUAL_TASK_CONFIRMATION_UNSUPPORTED);
    }

    @Test
    @DisplayName("manualTask + confirmationRequired (value attr) is rejected at deploy")
    void manualTaskConfirmAttr_rejected() {
        assertThatThrownBy(() -> deploy("manual-confirm-attr", MANUAL_CONFIRM_ATTR))
                .hasMessageContaining(WerkflowManualTaskValidator.WERKFLOW_MANUAL_TASK_CONFIRMATION_UNSUPPORTED);
    }

    @Test
    @DisplayName("manualTask + confirmationRequired (direct text node) is rejected at deploy")
    void manualTaskConfirmDirectText_rejected() {
        assertThatThrownBy(() -> deploy("manual-confirm-text", MANUAL_CONFIRM_DIRECT_TEXT))
                .hasMessageContaining(WerkflowManualTaskValidator.WERKFLOW_MANUAL_TASK_CONFIRMATION_UNSUPPORTED);
    }

    @Test
    @DisplayName("plain pass-through manualTask deploys cleanly")
    void plainManualTask_deploys() {
        assertThatCode(() -> deploy("manual-plain", MANUAL_PLAIN)).doesNotThrowAnyException();
    }
}
