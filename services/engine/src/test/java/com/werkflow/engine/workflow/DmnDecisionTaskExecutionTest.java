package com.werkflow.engine.workflow;

import org.flowable.common.engine.api.FlowableException;
import org.flowable.dmn.engine.configurator.DmnEngineConfigurator;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves, against a live in-memory Flowable 7.2 engine (same engine + DMN configurator as
 * production), which BPMN authoring form actually evaluates a DMN decision.
 *
 * <p>The ADR-026 re-audit (bytecode) concluded that {@code <businessRuleTask flowable:decisionRef=...>}
 * is dead config in Flowable 7.2 — the engine routes {@code businessRuleTask} to the legacy
 * Drools behaviour and never reaches {@code DmnActivityBehavior}. The only working form is
 * {@code <serviceTask flowable:type="dmn">} + a {@code decisionTableReferenceKey} field extension.
 *
 * <p>Both tests evaluate the SAME deployed decision ({@code procurement_matrix}) with the SAME
 * inputs (amount=1000, category=OFFICE → expected {@code DIRECT_PURCHASE}); the authoring form is
 * the only variable. A {@code userTask} parks the instance after the decision so the output
 * variable can be read from runtime scope without relying on a history level.
 *
 * <p>Standalone (no Spring / Postgres / Vault) because the behaviour under test is purely the
 * engine's element-to-behaviour mapping.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("DMN Decision Task — authoring-form execution (Flowable 7.2 contract)")
class DmnDecisionTaskExecutionTest {

    private static final Map<String, Object> SMALL_OFFICE_SPEND =
            Map.of("amount", 1000, "category", "OFFICE");

    /**
     * Minimal, JUEL-safe decision keyed {@code procurement_matrix}. Deliberately avoids FEEL
     * range syntax (e.g. {@code (50000..200000]}) which Flowable's default decision-table
     * evaluator cannot parse — so this test isolates the BPMN-to-DMN wiring contract, not DMN
     * content. amount=1000 matches the first rule → {@code DIRECT_PURCHASE}.
     */
    private static final String DMN_XML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
                     id="procurement_matrix_test_defs" name="Procurement Matrix Test"
                     namespace="http://werkflow.com/dmn">
          <decision id="procurement_matrix" name="Procurement Approval Matrix">
            <decisionTable id="t" hitPolicy="FIRST">
              <input id="in_amount" label="Amount">
                <inputExpression id="in_amount_expr" typeRef="number"><text>amount</text></inputExpression>
              </input>
              <output id="out_path" label="Path" name="procurementPath" typeRef="string"/>
              <rule id="r_direct">
                <inputEntry id="r_direct_in"><text>&lt;= 50000</text></inputEntry>
                <outputEntry id="r_direct_out"><text>"DIRECT_PURCHASE"</text></outputEntry>
              </rule>
              <rule id="r_manager">
                <inputEntry id="r_manager_in"><text>&gt; 50000</text></inputEntry>
                <outputEntry id="r_manager_out"><text>"MANAGER_APPROVAL"</text></outputEntry>
              </rule>
            </decisionTable>
          </decision>
        </definitions>
        """;

    /** Native DMN decision task, parked on a userTask so the output variable is readable. */
    private static final String SERVICETASK_DMN_PROCESS = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:flowable="http://flowable.org/bpmn"
                     targetNamespace="http://werkflow.com/bpmn/test">
          <process id="dmn-servicetask-form" name="DMN ServiceTask Form" isExecutable="true">
            <startEvent id="start"/>
            <sequenceFlow id="f1" sourceRef="start" targetRef="evaluate"/>
            <serviceTask id="evaluate" name="Evaluate Procurement Path" flowable:type="dmn">
              <extensionElements>
                <flowable:field name="decisionTableReferenceKey">
                  <flowable:string>procurement_matrix</flowable:string>
                </flowable:field>
              </extensionElements>
            </serviceTask>
            <sequenceFlow id="f2" sourceRef="evaluate" targetRef="park"/>
            <userTask id="park" name="Park"/>
            <sequenceFlow id="f3" sourceRef="park" targetRef="end"/>
            <endEvent id="end"/>
          </process>
        </definitions>
        """;

    private ProcessEngine processEngine;
    private RepositoryService repositoryService;
    private RuntimeService runtimeService;
    private String deploymentId;

    @BeforeAll
    void bootEngine() {
        StandaloneInMemProcessEngineConfiguration cfg = new StandaloneInMemProcessEngineConfiguration();
        cfg.setJdbcUrl("jdbc:h2:mem:dmnFormContract;DB_CLOSE_DELAY=1000");
        cfg.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        // Wire the DMN engine exactly as the Spring Boot starter does in production.
        cfg.addConfigurator(new DmnEngineConfigurator());
        processEngine = cfg.buildProcessEngine();
        repositoryService = processEngine.getRepositoryService();
        runtimeService = processEngine.getRuntimeService();
    }

    @AfterAll
    void shutdown() {
        if (processEngine != null) {
            processEngine.close();
        }
    }

    @AfterEach
    void cleanupDeployment() {
        if (deploymentId != null) {
            repositoryService.deleteDeployment(deploymentId, true);
            deploymentId = null;
        }
    }

    @Test
    @DisplayName("serviceTask flowable:type=dmn evaluates the DMN and sets procurementPath")
    void nativeServiceTaskDmnForm_evaluatesAndSetsProcurementPath() {
        deployInlineDmn(SERVICETASK_DMN_PROCESS);

        ProcessInstance pi = runtimeService.startProcessInstanceByKey(
                "dmn-servicetask-form", SMALL_OFFICE_SPEND);

        assertThat(runtimeService.getVariable(pi.getId(), "procurementPath"))
                .as("serviceTask type=dmn must evaluate the decision and set procurementPath")
                .isEqualTo("DIRECT_PURCHASE");
    }

    private String route(Map<String, Object> vars) {
        ProcessInstance pi = runtimeService.startProcessInstanceByKey("dmn-servicetask-form", vars);
        return (String) runtimeService.getVariable(pi.getId(), "procurementPath");
    }

    /**
     * Regression guard for the ADR-026 re-audit finding: authoring a DMN decision as
     * {@code <businessRuleTask flowable:decisionRef=...>} is dead config in Flowable 7.2.
     * The engine routes {@code businessRuleTask} to the legacy Drools/KIE rule behaviour, which
     * either (a) fails to wire when Drools is absent from the classpath — as here, a
     * {@code NoClassDefFoundError} for {@code org.kie.api...} at deploy — or (b) throws
     * {@code FlowableException: deployment ... doesn't contain any rules} at runtime when Drools
     * is present. In neither case is the DMN evaluated. {@code flowable:decisionRef} /
     * {@code flowable:mapDecisionResult} are inert. Use {@code serviceTask flowable:type="dmn"}.
     */
    @Test
    @DisplayName("businessRuleTask flowable:decisionRef is dead config — routes to Drools, never DMN")
    void businessRuleTaskForm_doesNotEvaluateDmn_isDeadConfig() {
        String brtProcess = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn"
                         targetNamespace="http://werkflow.com/bpmn/test">
              <process id="dmn-brt-form" name="DMN BusinessRuleTask Form" isExecutable="true">
                <startEvent id="start"/>
                <sequenceFlow id="f1" sourceRef="start" targetRef="evaluate"/>
                <businessRuleTask id="evaluate" name="Evaluate Procurement Path"
                                  flowable:decisionRef="procurement_matrix"
                                  flowable:mapDecisionResult="singleEntry"
                                  flowable:resultVariable="procurementPath"/>
                <sequenceFlow id="f2" sourceRef="evaluate" targetRef="park"/>
                <userTask id="park" name="Park"/>
                <sequenceFlow id="f3" sourceRef="park" targetRef="end"/>
                <endEvent id="end"/>
              </process>
            </definitions>
            """;

        // The failure mode is the Drools/KIE path, not DMN: without Drools on the classpath the
        // behaviour fails to wire at deploy (NoClassDefFoundError org.kie...); with Drools present
        // it throws FlowableException ("doesn't contain any rules") at runtime. Pin to those so a
        // scaffolding bug (e.g. AssertionError, bad process key) can't masquerade as the proof.
        assertThatThrownBy(() -> {
            deployInlineDmn(brtProcess);
            runtimeService.startProcessInstanceByKey("dmn-brt-form", SMALL_OFFICE_SPEND);
        })
                .as("businessRuleTask + decisionRef must NOT behave like a DMN task — "
                        + "it routes to the legacy Drools rule engine and never evaluates the decision")
                .isInstanceOfAny(NoClassDefFoundError.class, FlowableException.class);
    }

    /** Deploys the given BPMN alongside the minimal JUEL-safe inline {@link #DMN_XML}. */
    private void deployInlineDmn(String bpmnXml) {
        Deployment deployment = repositoryService.createDeployment()
                .addString("procurement-matrix.dmn", DMN_XML)
                .addString("process.bpmn20.xml", bpmnXml)
                .name("inline-dmn-test")
                .deploy();
        this.deploymentId = deployment.getId();
    }
}
