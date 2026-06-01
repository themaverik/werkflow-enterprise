package com.werkflow.engine.workflow;

import com.werkflow.engine.dto.dmn.DmnDecisionDto;
import com.werkflow.engine.service.BundleDeploymentService;
import com.werkflow.engine.service.DmnDecisionService;
import com.werkflow.engine.service.ProcessDefinitionService;
import com.werkflow.engine.testsupport.WerkflowTestProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * DoD test for ADR-026 Phase 3 bundle rollback — proves that
 * {@link BundleDeploymentService#rollbackToBundleVersion} produces a process definition that
 * the Flowable engine can actually execute, and that saga compensation is all-or-nothing.
 *
 * <p>Uses {@link WerkflowTestProcessEngine} so the same parse handlers and validators as
 * production are applied. No Spring context, no Postgres, no Vault.
 *
 * <p>Two tests cover the two DoD criteria:
 * <ol>
 *   <li><b>Happy path</b> — after rollback, a new process instance can be started and reaches
 *       its first user task (the redeployed definition is runnable).</li>
 *   <li><b>Atomicity</b> — when a DMN redeploy throws during rollback, no extra BPMN process
 *       definition appears as visible in the engine (saga compensation deleted it), and the
 *       bundle repository never receives a {@code save} call.</li>
 * </ol>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Bundle rollback execution DoD — real engine deploy + saga compensation")
class BundleRollbackExecutionTest {

    private static final String TENANT = "rollback-tenant";

    /**
     * Minimal executable BPMN: startEvent → userTask (parks here) → endEvent.
     * Simple by design — the DoD goal is proving the redeployed definition is runnable.
     */
    private static final String SIMPLE_BPMN_TEMPLATE = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:flowable="http://flowable.org/bpmn"
                     targetNamespace="http://werkflow.com/bpmn/test">
          <process id="%s" name="Rollback Test" isExecutable="true">
            <startEvent id="start"/>
            <sequenceFlow id="f1" sourceRef="start" targetRef="approve"/>
            <userTask id="approve" name="Approve"/>
            <sequenceFlow id="f2" sourceRef="approve" targetRef="end"/>
            <endEvent id="end"/>
          </process>
        </definitions>
        """;

    /**
     * Minimal DMN bundled alongside the BPMN in the source bundle.
     */
    private static final String SIMPLE_DMN_TEMPLATE = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
                     id="%s_defs" name="Rollback Test DMN" namespace="http://werkflow.com/dmn">
          <decision id="%s" name="Rollback Test Decision">
            <decisionTable id="t" hitPolicy="FIRST">
              <input id="in1" label="Value">
                <inputExpression id="in1e" typeRef="number"><text>value</text></inputExpression>
              </input>
              <output id="out1" label="Result" name="result" typeRef="string"/>
              <rule id="r1">
                <inputEntry id="r1i"><text>&gt;= 0</text></inputEntry>
                <outputEntry id="r1o"><text>"OK"</text></outputEntry>
              </rule>
            </decisionTable>
          </decision>
        </definitions>
        """;

    private WerkflowTestProcessEngine testEngine;
    private RepositoryService repositoryService;
    private RuntimeService runtimeService;
    private TaskService taskService;

    @BeforeAll
    void bootEngine() {
        testEngine = WerkflowTestProcessEngine.build("bundleRollbackExecution");
        repositoryService = testEngine.getProcessEngine().getRepositoryService();
        runtimeService   = testEngine.getProcessEngine().getRuntimeService();
        taskService      = testEngine.getProcessEngine().getTaskService();
    }

    @AfterAll
    void shutdown() {
        if (testEngine != null) {
            testEngine.close();
        }
    }

    // -------------------------------------------------------------------------
    // Test (a) — happy path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("(a) rolled-back process definition is runnable — new instance reaches first user task")
    void happyPath_rolledBackProcess_isRunnable() {
        String processKey = "rb-happy-proc";
        String dmnKey     = "rb_happy_dmn";
        String bpmn       = SIMPLE_BPMN_TEMPLATE.formatted(processKey);
        String dmn        = SIMPLE_DMN_TEMPLATE.formatted(dmnKey, dmnKey);

        ProcessDefinitionService pds = new ProcessDefinitionService(
                repositoryService, null,
                mock(BpmnIndicatorScanner.class), mock(ProcessIndicatorRepository.class));
        DmnDecisionService dmnSvc = new DmnDecisionService(
                testEngine.getDmnRepositoryService(),
                testEngine.getFlowableDmnDecisionService(),
                testEngine.getDmnHistoryService());

        // Seed source bundle v1 directly via the real services.
        String sourceParentId = TENANT + ":" + processKey + ":bundle:1";
        pds.deployProcessDefinition(bpmn, processKey + ".bpmn20.xml", sourceParentId, TENANT);
        dmnSvc.deployDecision(dmn, dmnKey, TENANT, sourceParentId);

        // Record source bundle as v1 in a Mockito-backed repo.
        ProcessBundle sourceBundle = ProcessBundle.builder()
                .tenantId(TENANT).processKey(processKey).bundleVersion(1)
                .parentDeploymentId(sourceParentId).createdBy("setup").build();

        AtomicInteger saveCount = new AtomicInteger(0);
        ProcessBundleRepository bundleRepo = mock(ProcessBundleRepository.class);
        when(bundleRepo.findByTenantIdAndProcessKeyAndBundleVersion(TENANT, processKey, 1))
                .thenReturn(Optional.of(sourceBundle));
        when(bundleRepo.findMaxBundleVersion(TENANT, processKey)).thenReturn(1);
        when(bundleRepo.save(any(ProcessBundle.class))).thenAnswer(inv -> {
            saveCount.incrementAndGet();
            return inv.getArgument(0);
        });

        BundleDeploymentService svc = new BundleDeploymentService(
                mock(BpmnBundleRefExtractor.class),
                mock(BpmnFormKeyPinner.class),
                pds, dmnSvc, bundleRepo);

        // Execute rollback.
        var result = svc.rollbackToBundleVersion(TENANT, processKey, 1, "test-rollback");

        // Result shape assertions.
        assertThat(result.bundleVersion()).isEqualTo(2);
        assertThat(result.bundledDecisions()).containsExactly(dmnKey);
        assertThat(saveCount.get()).as("bundle row must be saved exactly once").isEqualTo(1);

        // DoD assertion: the redeployed definition must be runnable.
        var pi = runtimeService.startProcessInstanceByKeyAndTenantId(processKey, TENANT);
        assertThat(pi).as("process instance should start without error").isNotNull();

        Task task = taskService.createTaskQuery()
                .processInstanceId(pi.getId())
                .taskDefinitionKey("approve")
                .singleResult();
        assertThat(task)
                .as("instance must park at the 'approve' user task — proving the redeployed definition is runnable")
                .isNotNull();
    }

    // -------------------------------------------------------------------------
    // Test (b) — atomicity: mid-rollback DMN failure triggers saga compensation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("(b) DMN failure during rollback: no extra BPMN definition in engine, bundle repo never saved")
    void atomicity_dmnFailure_compensatesAndNoLatestLeaks() {
        String processKey = "rb-atomic-proc";
        String dmnKey     = "rb_atomic_dmn";
        String bpmn       = SIMPLE_BPMN_TEMPLATE.formatted(processKey);
        String dmn        = SIMPLE_DMN_TEMPLATE.formatted(dmnKey, dmnKey);

        ProcessDefinitionService pds = new ProcessDefinitionService(
                repositoryService, null,
                mock(BpmnIndicatorScanner.class), mock(ProcessIndicatorRepository.class));
        DmnDecisionService realDmn = new DmnDecisionService(
                testEngine.getDmnRepositoryService(),
                testEngine.getFlowableDmnDecisionService(),
                testEngine.getDmnHistoryService());

        // Seed source bundle v1.
        String sourceParentId = TENANT + ":" + processKey + ":bundle:1";
        pds.deployProcessDefinition(bpmn, processKey + ".bpmn20.xml", sourceParentId, TENANT);
        realDmn.deployDecision(dmn, dmnKey, TENANT, sourceParentId);

        // Count BPMN process definitions for this key before the failed rollback.
        long definitionsBeforeRollback = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .processDefinitionTenantId(TENANT)
                .count();
        assertThat(definitionsBeforeRollback).as("exactly v1 exists before rollback").isEqualTo(1);

        ProcessBundle sourceBundle = ProcessBundle.builder()
                .tenantId(TENANT).processKey(processKey).bundleVersion(1)
                .parentDeploymentId(sourceParentId).createdBy("setup").build();

        AtomicInteger saveCount = new AtomicInteger(0);
        ProcessBundleRepository bundleRepo = mock(ProcessBundleRepository.class);
        when(bundleRepo.findByTenantIdAndProcessKeyAndBundleVersion(TENANT, processKey, 1))
                .thenReturn(Optional.of(sourceBundle));
        when(bundleRepo.findMaxBundleVersion(TENANT, processKey)).thenReturn(1);
        when(bundleRepo.save(any(ProcessBundle.class))).thenAnswer(inv -> {
            saveCount.incrementAndGet();
            return inv.getArgument(0);
        });

        // Spy on DmnDecisionService: force deployDecision to throw, simulating a mid-rollback failure.
        // Use doThrow().when() (not when().thenThrow()) to avoid calling the real method during stub setup —
        // the real method validates its args and would NPE on the Mockito null-placeholder arguments.
        DmnDecisionService dmnSpy = spy(realDmn);
        org.mockito.Mockito.doThrow(new RuntimeException("simulated DMN failure during rollback"))
                .when(dmnSpy).deployDecision(any(), any(), any(), any());

        BundleDeploymentService svc = new BundleDeploymentService(
                mock(BpmnBundleRefExtractor.class),
                mock(BpmnFormKeyPinner.class),
                pds, dmnSpy, bundleRepo);

        // The rollback must throw the original exception.
        assertThatThrownBy(() -> svc.rollbackToBundleVersion(TENANT, processKey, 1, "test-rollback-fail"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("simulated DMN failure during rollback");

        // Bundle row must never be saved.
        assertThat(saveCount.get())
                .as("bundleRepository.save must not be called when rollback fails mid-flight")
                .isEqualTo(0);

        // DoD assertion: saga compensation must have deleted the orphan BPMN deployment.
        // The definition count must not have grown beyond the pre-rollback baseline.
        long definitionsAfterFailure = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .processDefinitionTenantId(TENANT)
                .count();
        assertThat(definitionsAfterFailure)
                .as("rollback saga compensation must delete the newly-deployed BPMN — "
                        + "definition count must equal the pre-rollback baseline")
                .isEqualTo(definitionsBeforeRollback);
    }
}
