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
 * DoD tests for ADR-027 task 7(b) — per-level decision routing in capex-approval-process.
 *
 * <p>Proves the six key routing paths by deploying the REAL capex BPMN from classpath and
 * executing it in a bare in-memory Flowable engine with stub delegates:
 *
 * <ul>
 *   <li><b>externalApiCallDelegate stub</b>: sets {@code budgetAvailable=true} and
 *       {@code capexRequestId="CAP-1"} on every invocation (no-op for update tasks that
 *       don't need new vars). This satisfies the budget gate and the approve/reject URL
 *       template expressions in both updateApproved and updateRejected.</li>
 *   <li><b>notificationDelegate stub</b>: no-op; prevents NPE from missing Spring email beans.</li>
 * </ul>
 *
 * <p>Test cases:
 * <ol>
 *   <li>amount=10000, manager approve → APPROVED; VP/CFO tasks never created.</li>
 *   <li>amount=10000, manager reject → REJECTED immediately (ADR-027 early-exit fix); no VP task.</li>
 *   <li>amount=100000, manager approve → VP task appears → VP approve → APPROVED; CFO never created.</li>
 *   <li>amount=100000, manager approve → VP reject → REJECTED (early-exit at VP level); CFO never.</li>
 *   <li>amount=10000, manager escalate → VP task appears (escalate bypasses amount check).</li>
 *   <li>amount=300000, manager approve → VP approve → CFO task appears → CFO approve → APPROVED.</li>
 * </ol>
 *
 * <p>Uses {@link WerkflowTestProcessEngine#build(String, Map)} with stub beans. No Spring context,
 * no Postgres, no Vault.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("ADR-027 capex per-level decision routing — 6 execution paths")
class CapexApprovalRoutingTest {

    /**
     * Stub JavaDelegate for {@code externalApiCallDelegate}.
     *
     * <p>Unconditionally sets {@code budgetAvailable=true} and {@code capexRequestId="CAP-1"} so
     * the budget gateway passes and the approve/reject path URLs resolve. For service tasks that
     * don't need these variables (e.g. updateApproved, updateRejected) the overwrite is harmless.
     */
    private static final org.flowable.engine.delegate.JavaDelegate EXTERNAL_API_STUB =
            execution -> {
                execution.setVariable("budgetAvailable", true);
                execution.setVariable("capexRequestId", "CAP-1");
            };

    /** No-op stub for {@code notificationDelegate} — prevents NPE from missing Spring email beans. */
    private static final org.flowable.engine.delegate.JavaDelegate NOTIFICATION_STUB =
            execution -> { /* intentional no-op */ };

    private static final Map<Object, Object> STUB_BEANS = Map.of(
            "externalApiCallDelegate", EXTERNAL_API_STUB,
            "notificationDelegate", NOTIFICATION_STUB
    );

    private WerkflowTestProcessEngine testEngine;
    private RepositoryService repositoryService;
    private RuntimeService runtimeService;
    private TaskService taskService;
    private HistoryService historyService;

    @BeforeAll
    void bootEngineAndDeploy() {
        testEngine = WerkflowTestProcessEngine.build("capexApprovalRouting", STUB_BEANS);
        repositoryService = testEngine.getProcessEngine().getRepositoryService();
        runtimeService   = testEngine.getProcessEngine().getRuntimeService();
        taskService      = testEngine.getProcessEngine().getTaskService();
        historyService   = testEngine.getProcessEngine().getHistoryService();

        // Deploy the REAL capex BPMN from classpath — same file as production uses.
        repositoryService.createDeployment()
                .addClasspathResource("processes/examples/capex-approval-process.bpmn20.xml")
                .name("capex-approval-routing-test")
                .deploy();
    }

    @AfterAll
    void shutdown() {
        if (testEngine != null) {
            testEngine.close();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Starts a process instance with requestAmount and returns it. */
    private ProcessInstance start(int requestAmount) {
        return runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                Map.of("requestAmount", requestAmount));
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

    /** Asserts the process instance has ended (no active runtime instance). */
    private void assertProcessEnded(String processInstanceId) {
        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId).count())
                .as("process instance must have ended")
                .isEqualTo(0);
    }

    /** Asserts that the given activity executed (appears in history as finished). */
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

    /** Asserts that the given activity never executed (absent from history). */
    private void assertActivityNotExecuted(String processInstanceId, String activityId) {
        List<HistoricActivityInstance> acts = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .activityId(activityId)
                .list();
        assertThat(acts)
                .as("activity '" + activityId + "' must NOT appear in history")
                .isEmpty();
    }

    /** Returns the active task definition key on this instance, or null if none. */
    private String activeTaskKey(String processInstanceId) {
        Task t = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        return t == null ? null : t.getTaskDefinitionKey();
    }

    // -------------------------------------------------------------------------
    // Case 1: amount=10000, manager approve → APPROVED; VP/CFO tasks never created
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("1. manager approve + amount<=50k → APPROVED without VP or CFO task")
    void case1_managerApprove_smallAmount_approved() {
        ProcessInstance pi = start(10_000);

        // Manager approves.
        completeWithDecision(pi.getId(), "approve");

        // Process must have ended.
        assertProcessEnded(pi.getId());

        // Approved path executed.
        assertActivityExecuted(pi.getId(), "updateApproved");
        assertActivityExecuted(pi.getId(), "sendApprovalNotification");
        assertActivityExecuted(pi.getId(), "endEventApproved");

        // VP and CFO tasks must not have been created.
        assertActivityNotExecuted(pi.getId(), "vpApproval");
        assertActivityNotExecuted(pi.getId(), "cfoApproval");

        // Rejected path must not have been touched.
        assertActivityNotExecuted(pi.getId(), "updateRejected");
        assertActivityNotExecuted(pi.getId(), "endEventRejected");
    }

    // -------------------------------------------------------------------------
    // Case 2: amount=10000, manager reject → REJECTED immediately (ADR-027 core fix)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("2. manager reject + amount<=50k → REJECTED immediately; no VP task (ADR-027 early-exit)")
    void case2_managerReject_earlyExitRejected() {
        ProcessInstance pi = start(10_000);

        // Manager rejects.
        completeWithDecision(pi.getId(), "reject");

        // Process must have ended.
        assertProcessEnded(pi.getId());

        // Rejected path executed.
        assertActivityExecuted(pi.getId(), "updateRejected");
        assertActivityExecuted(pi.getId(), "sendRejectionNotification");
        assertActivityExecuted(pi.getId(), "endEventRejected");

        // VP and CFO must never have been reached.
        assertActivityNotExecuted(pi.getId(), "vpApproval");
        assertActivityNotExecuted(pi.getId(), "cfoApproval");

        // Approved path must not have been touched.
        assertActivityNotExecuted(pi.getId(), "updateApproved");
        assertActivityNotExecuted(pi.getId(), "endEventApproved");
    }

    // -------------------------------------------------------------------------
    // Case 3: amount=100000, manager approve → VP task → VP approve → APPROVED; CFO never
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("3. manager approve + amount>50k → VP task; VP approve → APPROVED; CFO never created")
    void case3_managerApprove_largeAmount_vpApprove_approved() {
        ProcessInstance pi = start(100_000);

        // Manager approves; amount>50k routes to VP.
        completeWithDecision(pi.getId(), "approve");

        // VP task must now be active.
        assertThat(activeTaskKey(pi.getId()))
                .as("VP task must be the active task after manager approves a >50k request")
                .isEqualTo("vpApproval");

        // CFO task must not have been created yet.
        assertActivityNotExecuted(pi.getId(), "cfoApproval");

        // VP approves; amount<=250k routes to updateApproved.
        completeWithDecision(pi.getId(), "approve");

        // Process must have ended on the approved path.
        assertProcessEnded(pi.getId());
        assertActivityExecuted(pi.getId(), "updateApproved");
        assertActivityExecuted(pi.getId(), "endEventApproved");

        // CFO must never have been reached.
        assertActivityNotExecuted(pi.getId(), "cfoApproval");
        assertActivityNotExecuted(pi.getId(), "updateRejected");
    }

    // -------------------------------------------------------------------------
    // Case 4: amount=100000, manager approve → VP task → VP reject → REJECTED (early-exit at VP)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("4. manager approve + amount>50k → VP task; VP reject → REJECTED; CFO never created")
    void case4_managerApprove_largeAmount_vpReject_earlyExitRejected() {
        ProcessInstance pi = start(100_000);

        // Manager approves; VP task appears.
        completeWithDecision(pi.getId(), "approve");
        assertThat(activeTaskKey(pi.getId()))
                .as("VP task must be active before VP rejects")
                .isEqualTo("vpApproval");

        // VP rejects.
        completeWithDecision(pi.getId(), "reject");

        // Process must end on rejected path.
        assertProcessEnded(pi.getId());
        assertActivityExecuted(pi.getId(), "updateRejected");
        assertActivityExecuted(pi.getId(), "endEventRejected");

        // CFO must never have been created.
        assertActivityNotExecuted(pi.getId(), "cfoApproval");
        assertActivityNotExecuted(pi.getId(), "updateApproved");
    }

    // -------------------------------------------------------------------------
    // Case 5: amount=10000, manager escalate → VP task appears (bypasses amount check)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("5. manager escalate + amount<=50k → VP task appears (escalate bypasses amount threshold)")
    void case5_managerEscalate_smallAmount_vpTaskAppears() {
        ProcessInstance pi = start(10_000);

        // Manager escalates; escalate routes directly to vpApproval regardless of amount.
        completeWithDecision(pi.getId(), "escalate");

        // VP task must now be active even though amount<=50k.
        assertThat(activeTaskKey(pi.getId()))
                .as("VP task must be active after manager escalates, even for amount<=50k")
                .isEqualTo("vpApproval");

        // Process must still be running.
        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(pi.getId()).count())
                .as("process must still be running (parked at VP task)")
                .isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Case 6: amount=300000, manager approve → VP approve → CFO task → CFO approve → APPROVED
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("6. amount>250k — manager approve → VP approve → CFO task → CFO approve → APPROVED")
    void case6_fullTierChain_allApprove_approved() {
        ProcessInstance pi = start(300_000);

        // Manager approves; amount>50k → VP task.
        completeWithDecision(pi.getId(), "approve");
        assertThat(activeTaskKey(pi.getId()))
                .as("VP task must be active after manager approves a >50k request")
                .isEqualTo("vpApproval");

        // VP approves; amount>250k → CFO task.
        completeWithDecision(pi.getId(), "approve");
        assertThat(activeTaskKey(pi.getId()))
                .as("CFO task must be active after VP approves a >250k request")
                .isEqualTo("cfoApproval");

        // CFO approves; top level → APPROVED.
        completeWithDecision(pi.getId(), "approve");

        // Process must have ended on the approved path.
        assertProcessEnded(pi.getId());
        assertActivityExecuted(pi.getId(), "updateApproved");
        assertActivityExecuted(pi.getId(), "sendApprovalNotification");
        assertActivityExecuted(pi.getId(), "endEventApproved");
        assertActivityNotExecuted(pi.getId(), "updateRejected");
    }
}
