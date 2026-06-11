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
        deploy("examples/tenants/default/dmn/leave-approval.dmn", "leave_approval");

        assertThat(route(Map.of("leaveDays", 2, "leaveType", "annual"), "approverRole"))
                .as("1–3 annual → auto").isEqualTo("AUTO");
        assertThat(route(Map.of("leaveDays", 2, "leaveType", "annual"), "approvalRequired"))
                .as("auto-approved → not required").isEqualTo(false);
        assertThat(route(Map.of("leaveDays", 3, "leaveType", "personal"), "approverRole"))
                .as("1–3 personal → auto").isEqualTo("AUTO");
        assertThat(route(Map.of("leaveDays", 5, "leaveType", "annual"), "approverRole"))
                .as("4–10 days → line manager").isEqualTo("LINE_MANAGER");
        assertThat(route(Map.of("leaveDays", 10, "leaveType", "personal"), "approverRole"))
                .as("boundary 10 days → line manager").isEqualTo("LINE_MANAGER");
        assertThat(route(Map.of("leaveDays", 15, "leaveType", "annual"), "approverRole"))
                .as("> 10 days → HR manager").isEqualTo("HR_MANAGER");
        assertThat(route(Map.of("leaveDays", 1, "leaveType", "sick"), "approverRole"))
                .as("sick (any duration) → HR manager").isEqualTo("HR_MANAGER");
        assertThat(route(Map.of("leaveDays", 20, "leaveType", "parental"), "approverRole"))
                .as("parental → HR manager").isEqualTo("HR_MANAGER");
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
