package com.werkflow.engine.workflow;

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

/**
 * Routing guards for the shipped sample decision tables, executed through Flowable's native
 * DMN service-task form. Flowable evaluates decision-table entries as JUEL (not FEEL), so FEEL
 * range syntax ({@code [4..10]}) and comma "one-of" lists ({@code "A","B"}) throw at evaluation.
 * These tests pin each decision's intended routing so the FEEL→JUEL conversion is behaviour-
 * preserving and so the tables stay evaluable before they are wired to any BPMN (Roadmap item 14).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Sample DMN decision tables — JUEL routing")
class DmnDecisionTablesJuelTest {

    private ProcessEngine processEngine;
    private RepositoryService repositoryService;
    private RuntimeService runtimeService;
    private String deploymentId;

    @BeforeAll
    void bootEngine() {
        StandaloneInMemProcessEngineConfiguration cfg = new StandaloneInMemProcessEngineConfiguration();
        cfg.setJdbcUrl("jdbc:h2:mem:dmnTablesJuel;DB_CLOSE_DELAY=1000");
        cfg.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
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
    @DisplayName("leave-approval.dmn routes by duration and leave type")
    void leaveApproval_routes() {
        deploy("dmn-examples/leave-approval.dmn", "leave_approval");

        assertThat(route(Map.of("leaveDays", 2, "leaveType", "CASUAL"), "approverRole"))
                .as("1–3 casual → auto").isEqualTo("AUTO");
        assertThat(route(Map.of("leaveDays", 2, "leaveType", "CASUAL"), "approvalRequired"))
                .as("auto-approved → not required").isEqualTo(false);
        assertThat(route(Map.of("leaveDays", 3, "leaveType", "ANNUAL"), "approverRole"))
                .as("1–3 annual → auto").isEqualTo("AUTO");
        assertThat(route(Map.of("leaveDays", 5, "leaveType", "ANNUAL"), "approverRole"))
                .as("4–10 days → line manager").isEqualTo("LINE_MANAGER");
        assertThat(route(Map.of("leaveDays", 10, "leaveType", "CASUAL"), "approverRole"))
                .as("boundary 10 days → line manager").isEqualTo("LINE_MANAGER");
        assertThat(route(Map.of("leaveDays", 15, "leaveType", "ANNUAL"), "approverRole"))
                .as("> 10 days → HR manager").isEqualTo("HR_MANAGER");
        assertThat(route(Map.of("leaveDays", 1, "leaveType", "MEDICAL"), "approverRole"))
                .as("medical (any duration) → HR manager").isEqualTo("HR_MANAGER");
        assertThat(route(Map.of("leaveDays", 20, "leaveType", "MATERNITY"), "approverRole"))
                .as("maternity → HR manager").isEqualTo("HR_MANAGER");
    }

    @Test
    @DisplayName("procurement-matrix.dmn routes by amount and category")
    void procurementMatrix_routes() {
        deploy("dmn-examples/procurement-matrix.dmn", "procurement_matrix");

        assertThat(route(Map.of("amount", 10000, "category", "SUPPLIES"), "procurementPath"))
                .as("<= 50k → direct purchase").isEqualTo("DIRECT_PURCHASE");
        assertThat(route(Map.of("amount", 10000, "category", "SUPPLIES"), "requiresCommittee"))
                .as("direct purchase → no committee").isEqualTo(false);
        assertThat(route(Map.of("amount", 75000, "category", "OFFICE"), "procurementPath"))
                .as("> 50k → manager approval").isEqualTo("MANAGER_APPROVAL");
        assertThat(route(Map.of("amount", 75000, "category", "OFFICE"), "requiresCommittee"))
                .as("manager approval → no committee").isEqualTo(false);
        assertThat(route(Map.of("amount", 250000, "category", "LOGISTICS"), "procurementPath"))
                .as("> 200k any category → committee review").isEqualTo("COMMITTEE_REVIEW");
        assertThat(route(Map.of("amount", 250000, "category", "LOGISTICS"), "requiresCommittee"))
                .as("committee review → committee required").isEqualTo(true);
        assertThat(route(Map.of("amount", 600000, "category", "IT"), "procurementPath"))
                .as("> 500k IT → board approval").isEqualTo("BOARD_APPROVAL");
        assertThat(route(Map.of("amount", 600000, "category", "IT"), "requiresCommittee"))
                .as("board approval → committee required").isEqualTo(true);
        assertThat(route(Map.of("amount", 600000, "category", "INFRASTRUCTURE"), "procurementPath"))
                .as("> 500k infrastructure → board approval").isEqualTo("BOARD_APPROVAL");
    }

    private void deploy(String dmnResource, String decisionKey) {
        String process = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn"
                         targetNamespace="http://werkflow.com/bpmn/test">
              <process id="dmn-table-test" isExecutable="true">
                <startEvent id="start"/>
                <sequenceFlow id="f1" sourceRef="start" targetRef="evaluate"/>
                <serviceTask id="evaluate" flowable:type="dmn">
                  <extensionElements>
                    <flowable:field name="decisionTableReferenceKey"><flowable:string>%s</flowable:string></flowable:field>
                  </extensionElements>
                </serviceTask>
                <sequenceFlow id="f2" sourceRef="evaluate" targetRef="park"/>
                <userTask id="park"/>
                <sequenceFlow id="f3" sourceRef="park" targetRef="end"/>
                <endEvent id="end"/>
              </process>
            </definitions>
            """.formatted(decisionKey);

        Deployment deployment = repositoryService.createDeployment()
                .addClasspathResource(dmnResource)
                .addString("dmn-table-test.bpmn20.xml", process)
                .name("dmn-table-test")
                .deploy();
        this.deploymentId = deployment.getId();
    }

    private Object route(Map<String, Object> vars, String outputVar) {
        ProcessInstance pi = runtimeService.startProcessInstanceByKey("dmn-table-test", vars);
        return runtimeService.getVariable(pi.getId(), outputVar);
    }
}
