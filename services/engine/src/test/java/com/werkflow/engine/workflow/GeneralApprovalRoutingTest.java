package com.werkflow.engine.workflow;

import com.werkflow.engine.testsupport.WerkflowTestProcessEngine;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DoD tests for ADR-027 task 7(c) — per-level decision routing in general-approval.bpmn20.xml.
 *
 * <p>Proves the five key routing paths:
 * <ol>
 *   <li>amount &le; 50k, manager approve   → APPROVED (no director task).</li>
 *   <li>amount &le; 50k, manager reject    → REJECTED immediately (early-exit).</li>
 *   <li>amount &gt; 50k, manager approve   → director task; director approve → APPROVED.</li>
 *   <li>amount &gt; 50k, manager approve   → director task; director reject  → REJECTED.</li>
 *   <li>amount &le; 50k, manager escalate  → director task appears (escalate bypasses amount check).</li>
 * </ol>
 *
 * <p>Uses {@link WerkflowTestProcessEngine#build(String, Map)} with a no-op
 * {@code notificationDelegate} stub. The process is engine-internal so no
 * {@code externalApiCallDelegate} is needed.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("ADR-027 general-approval per-level decision routing — 5 execution paths")
class GeneralApprovalRoutingTest {

    /** No-op stub — general-approval only uses notificationDelegate (via GlobalTaskNotificationListener). */
    private static final org.flowable.engine.delegate.JavaDelegate NOTIFICATION_STUB =
            execution -> { /* intentional no-op */ };

    private static final Map<Object, Object> STUB_BEANS = Map.of(
            "notificationDelegate", NOTIFICATION_STUB
    );

    private WerkflowTestProcessEngine testEngine;
    private RepositoryService repositoryService;
    private RuntimeService runtimeService;
    private TaskService taskService;
    private HistoryService historyService;

    @BeforeAll
    void bootEngineAndDeploy() {
        testEngine = WerkflowTestProcessEngine.build("generalApprovalRouting", STUB_BEANS);
        repositoryService = testEngine.getProcessEngine().getRepositoryService();
        runtimeService   = testEngine.getProcessEngine().getRuntimeService();
        taskService      = testEngine.getProcessEngine().getTaskService();
        historyService   = testEngine.getProcessEngine().getHistoryService();

        repositoryService.createDeployment()
                .addClasspathResource("processes/examples/general-approval.bpmn20.xml")
                .name("general-approval-routing-test")
                .deploy();
    }

    @AfterAll
    void shutdown() {
        if (testEngine != null) {
            testEngine.close();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers (mirror CapexApprovalRoutingTest pattern exactly)
    // -------------------------------------------------------------------------

    /**
     * Starts a process instance bypassing the submitRequest user task (which requires an
     * assignee in production) by supplying amount directly at start. In the in-memory test
     * engine the submitRequest task is created and we complete it immediately so the flow
     * advances to managerApproval.
     */
    private ProcessInstance startAndSubmit(int amount) {
        ProcessInstance pi = runtimeService.startProcessInstanceByKey(
                "general-approval",
                Map.of("initiator", "testUser", "amount", amount));
        // Complete the submitRequest task that the initiator must fill in.
        Task submitTask = taskService.createTaskQuery()
                .processInstanceId(pi.getId())
                .taskDefinitionKey("submitRequest")
                .singleResult();
        if (submitTask != null) {
            taskService.complete(submitTask.getId(),
                    Map.of("title", "Test Request", "description", "Test", "amount", amount));
        }
        return pi;
    }

    /** Completes the single active task on this instance with the given decision value. */
    private void completeWithDecision(String processInstanceId, String decision) {
        Task task = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        assertThat(task)
                .as("expected one active task on process instance " + processInstanceId)
                .isNotNull();
        taskService.complete(task.getId(), Map.of("decision", decision));
    }

    /** Asserts the process instance has ended. */
    private void assertProcessEnded(String processInstanceId) {
        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId).count())
                .as("process instance must have ended")
                .isEqualTo(0);
    }

    /** Asserts a given activity executed (finished in history). */
    private void assertActivityExecuted(String processInstanceId, String activityId) {
        List<HistoricActivityInstance> acts = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .activityId(activityId)
                .finished()
                .list();
        assertThat(acts)
                .as("activity '" + activityId + "' must appear as finished in history")
                .isNotEmpty();
    }

    /** Asserts a given activity never executed. */
    private void assertActivityNotExecuted(String processInstanceId, String activityId) {
        List<HistoricActivityInstance> acts = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .activityId(activityId)
                .list();
        assertThat(acts)
                .as("activity '" + activityId + "' must NOT appear in history")
                .isEmpty();
    }

    /** Returns the active task definition key, or null if none. */
    private String activeTaskKey(String processInstanceId) {
        Task t = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        return t == null ? null : t.getTaskDefinitionKey();
    }

    // -------------------------------------------------------------------------
    // Case 1: amount<=50k, manager approve → APPROVED; no director task
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("1. amount<=50k + manager approve → APPROVED (no director task)")
    void case1_managerApprove_smallAmount_approved() {
        ProcessInstance pi = startAndSubmit(30_000);

        // Manager approves.
        completeWithDecision(pi.getId(), "approve");

        assertProcessEnded(pi.getId());
        assertActivityExecuted(pi.getId(), "endApproved");
        assertActivityNotExecuted(pi.getId(), "directorApproval");
        assertActivityNotExecuted(pi.getId(), "endRejected");
    }

    // -------------------------------------------------------------------------
    // Case 2: amount<=50k, manager reject → REJECTED immediately (early-exit)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("2. amount<=50k + manager reject → REJECTED immediately; no director task (ADR-027 early-exit)")
    void case2_managerReject_earlyExitRejected() {
        ProcessInstance pi = startAndSubmit(30_000);

        // Manager rejects.
        completeWithDecision(pi.getId(), "reject");

        assertProcessEnded(pi.getId());
        assertActivityExecuted(pi.getId(), "endRejected");

        // Director must never have been reached.
        assertActivityNotExecuted(pi.getId(), "directorApproval");
        assertActivityNotExecuted(pi.getId(), "endApproved");
    }

    // -------------------------------------------------------------------------
    // Case 3: amount>50k, manager approve → director task; director approve → APPROVED
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("3. amount>50k + manager approve → director task; director approve → APPROVED")
    void case3_managerApprove_largeAmount_directorApprove_approved() {
        ProcessInstance pi = startAndSubmit(75_000);

        // Manager approves; amount>50k → director task.
        completeWithDecision(pi.getId(), "approve");

        assertThat(activeTaskKey(pi.getId()))
                .as("director task must be active after manager approves a >50k request")
                .isEqualTo("directorApproval");

        // Director approves.
        completeWithDecision(pi.getId(), "approve");

        assertProcessEnded(pi.getId());
        assertActivityExecuted(pi.getId(), "endApproved");
        assertActivityNotExecuted(pi.getId(), "endRejected");
    }

    // -------------------------------------------------------------------------
    // Case 4: amount>50k, manager approve → director task; director reject → REJECTED
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("4. amount>50k + manager approve → director task; director reject → REJECTED")
    void case4_managerApprove_largeAmount_directorReject_rejected() {
        ProcessInstance pi = startAndSubmit(75_000);

        // Manager approves; director task appears.
        completeWithDecision(pi.getId(), "approve");
        assertThat(activeTaskKey(pi.getId()))
                .as("director task must be active before director rejects")
                .isEqualTo("directorApproval");

        // Director rejects.
        completeWithDecision(pi.getId(), "reject");

        assertProcessEnded(pi.getId());
        assertActivityExecuted(pi.getId(), "endRejected");
        assertActivityNotExecuted(pi.getId(), "endApproved");
    }

    // -------------------------------------------------------------------------
    // Case 5: amount<=50k, manager escalate → director task appears (escalate bypasses amount)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("5. amount<=50k + manager escalate → director task appears (escalate bypasses amount threshold)")
    void case5_managerEscalate_smallAmount_directorTaskAppears() {
        ProcessInstance pi = startAndSubmit(30_000);

        // Manager escalates; route goes directly to directorApproval regardless of amount.
        completeWithDecision(pi.getId(), "escalate");

        assertThat(activeTaskKey(pi.getId()))
                .as("director task must be active after manager escalates, even for amount<=50k")
                .isEqualTo("directorApproval");

        // Process must still be running.
        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(pi.getId()).count())
                .as("process must still be running (parked at director task)")
                .isEqualTo(1);
    }
}
