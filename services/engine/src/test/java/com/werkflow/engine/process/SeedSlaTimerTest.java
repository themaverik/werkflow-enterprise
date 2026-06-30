package com.werkflow.engine.process;

import com.werkflow.engine.testsupport.ProcessTestDsl;
import com.werkflow.engine.testsupport.WerkflowTestProcessEngine;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.job.api.Job;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Process-level verification of SLA timer boundary events on HUMAN_APPROVAL UserTasks
 * across all four approval seed BPMNs.
 *
 * <p>Two core scenarios are exercised against the leave-request seed (single-level, simplest):
 * <ol>
 *   <li><b>Within-SLA (happy path)</b> — task is completed before the timer fires;
 *       the SLA-Breached end event is never reached and the process ends normally.</li>
 *   <li><b>SLA breach</b> — the timer is fired synthetically via
 *       {@link ManagementService#moveTimerToExecutableJob} without completing the task;
 *       the SLA-Breached end event is reached (non-interrupting), and the approval task
 *       remains active.</li>
 * </ol>
 *
 * <p>Additionally:
 * <ul>
 *   <li>All four seeds deploy through {@link WerkflowTestProcessEngine} without validation errors.</li>
 *   <li>The {@code slaDuration} default mechanism is verified: when the variable is absent the
 *       timer registers with a non-null due date (PT15M default resolved by JUEL ternary); when
 *       the variable is set to a shorter value the timer's due date reflects the override.</li>
 * </ul>
 */
@Tag("flow")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Seed SLA timer boundary events")
class SeedSlaTimerTest {

    private static final String LEAVE_DMN  = "examples/tenants/default/dmn/leave-approval.dmn";
    private static final String LEAVE_BPMN = "examples/tenants/default/bpmn/leave-request.bpmn20.xml";
    private static final String CAPEX_BPMN = "examples/tenants/default/bpmn/capex-approval-process.bpmn20.xml";
    private static final String PROC_BPMN  = "examples/tenants/default/bpmn/procurement-approval-process.bpmn20.xml";
    private static final String DESK_BPMN  = "examples/tenants/default/bpmn/it-helpdesk-ticket.bpmn20.xml";

    /** Leave-request start vars that route via DMN to the managerReview HUMAN_APPROVAL task. */
    private static final Map<String, Object> SICK_LEAVE = Map.of(
            "leaveDays", 5,
            "leaveType", "sick"
    );

    private WerkflowTestProcessEngine engine;
    private ManagementService managementService;
    private RuntimeService runtimeService;
    private TaskService taskService;
    private HistoryService historyService;
    private ProcessTestDsl dsl;

    @BeforeAll
    void setUp() {
        engine = WerkflowTestProcessEngine.build("seedSlaTimer");
        managementService = engine.getProcessEngine().getManagementService();
        runtimeService    = engine.getProcessEngine().getRuntimeService();
        taskService       = engine.getProcessEngine().getTaskService();
        historyService    = engine.getProcessEngine().getHistoryService();
        dsl = new ProcessTestDsl(engine.getProcessEngine());

        // DMN must be deployed before the BPMN that uses it.
        dsl.deploy(LEAVE_DMN)
           .deploy(LEAVE_BPMN)
           .deploy(CAPEX_BPMN)
           .deploy(PROC_BPMN)
           .deploy(DESK_BPMN);
    }

    @AfterAll
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    // ── Deployment ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("All 4 seed BPMNs deploy without Werkflow validator errors")
    void allSeedsDeploy_clean() {
        // Deployment happens in @BeforeAll — reaching here proves no ValidationException was thrown.
        // Assert each seed's process definition is present and queryable.
        assertThat(engine.getProcessEngine().getRepositoryService()
                .createProcessDefinitionQuery()
                .processDefinitionKey("leave-request")
                .count())
                .as("leave-request deployed").isEqualTo(1);
        assertThat(engine.getProcessEngine().getRepositoryService()
                .createProcessDefinitionQuery()
                .processDefinitionKey("capex-approval-process")
                .count())
                .as("capex-approval-process deployed").isEqualTo(1);
        assertThat(engine.getProcessEngine().getRepositoryService()
                .createProcessDefinitionQuery()
                .processDefinitionKey("procurement-approval-process")
                .count())
                .as("procurement-approval-process deployed").isEqualTo(1);
        assertThat(engine.getProcessEngine().getRepositoryService()
                .createProcessDefinitionQuery()
                .processDefinitionKey("it-helpdesk-ticket")
                .count())
                .as("it-helpdesk-ticket deployed").isEqualTo(1);
    }

    // ── Happy path: complete before timer fires ────────────────────────────────

    @Test
    @DisplayName("Within-SLA: completing approval before timer fires ends process normally, SLA-Breached never reached")
    void withinSla_completingTask_endsNormally() {
        ProcessInstance pi = runtimeService.startProcessInstanceByKey("leave-request", SICK_LEAVE);
        String pid = pi.getId();

        // Confirm we are at the approval task (DMN routed us here).
        Task approvalTask = taskService.createTaskQuery()
                .processInstanceId(pid)
                .taskDefinitionKey("managerReview")
                .singleResult();
        assertThat(approvalTask).as("managerReview task must be active").isNotNull();

        // Complete immediately (within SLA) — this cancels the timer boundary.
        taskService.complete(approvalTask.getId(), Map.of("decision", "approve"));

        // Process should be finished.
        long finished = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(pid).finished().count();
        assertThat(finished).as("process should be completed").isEqualTo(1);

        // SLA-Breached end event must NOT appear in the activity history.
        List<HistoricActivityInstance> slaActivities = historyService
                .createHistoricActivityInstanceQuery()
                .processInstanceId(pid)
                .activityId("endEventSlaBreached")
                .list();
        assertThat(slaActivities).as("endEventSlaBreached must not be reached in within-SLA scenario").isEmpty();

        // No timer jobs should remain for this instance.
        long timerCount = managementService.createTimerJobQuery()
                .processInstanceId(pid).count();
        assertThat(timerCount).as("timer job must be cancelled after task completion").isEqualTo(0);
    }

    // ── SLA breach: fire timer without completing the task ─────────────────────

    @Test
    @DisplayName("SLA breach: firing timer reaches SLA-Breached end event; approval task remains active (non-interrupting)")
    void slaBreached_firingTimer_reachesBreachedEnd_taskStillActive() {
        ProcessInstance pi = runtimeService.startProcessInstanceByKey("leave-request", SICK_LEAVE);
        String pid = pi.getId();

        // Confirm approval task is active.
        Task approvalTask = taskService.createTaskQuery()
                .processInstanceId(pid)
                .taskDefinitionKey("managerReview")
                .singleResult();
        assertThat(approvalTask).as("managerReview task must be active before breach").isNotNull();

        // Locate the SLA timer job without completing the task.
        Job timerJob = managementService.createTimerJobQuery()
                .processInstanceId(pid)
                .singleResult();
        assertThat(timerJob).as("timer job must exist for the active boundary event").isNotNull();

        // Move timer to executable table, then execute synchronously.
        Job executableJob = managementService.moveTimerToExecutableJob(timerJob.getId());
        managementService.executeJob(executableJob.getId());

        // SLA-Breached end event must appear in history (the timer-spawned token reached it).
        List<HistoricActivityInstance> slaActivities = historyService
                .createHistoricActivityInstanceQuery()
                .processInstanceId(pid)
                .activityId("endEventSlaBreached")
                .list();
        assertThat(slaActivities).as("endEventSlaBreached must be recorded in history after timer fires").hasSize(1);

        // The approval task must still be active (non-interrupting — original token survives).
        Task stillActive = taskService.createTaskQuery()
                .processInstanceId(pid)
                .taskDefinitionKey("managerReview")
                .singleResult();
        assertThat(stillActive).as("managerReview task must still be active after non-interrupting timer fires").isNotNull();

        // The process instance must NOT be completed (approval task is still pending).
        long finished = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(pid).finished().count();
        assertThat(finished).as("process must still be running (approval task active)").isEqualTo(0);
    }

    // ── slaDuration variable: default and override ─────────────────────────────

    @Test
    @DisplayName("slaDuration default: when variable absent, timer job registers with ~15 min due date (JUEL ternary)")
    void slaDuration_absent_defaultsToFifteenMinutes() {
        ProcessInstance pi = runtimeService.startProcessInstanceByKey("leave-request", SICK_LEAVE);
        String pid = pi.getId();

        Job timerJob = managementService.createTimerJobQuery()
                .processInstanceId(pid)
                .singleResult();
        assertThat(timerJob).as("timer job must exist when slaDuration not set").isNotNull();
        assertThat(timerJob.getDuedate()).as("timer job must have a due date").isNotNull();

        // Due date should be approximately now + 15 minutes (within 30 seconds tolerance).
        Instant expectedDue = Instant.now().plus(Duration.ofMinutes(15));
        Instant actualDue = timerJob.getDuedate().toInstant();
        assertThat(Duration.between(actualDue, expectedDue).abs())
                .as("timer due date should be ~15 min from now (PT15M default)")
                .isLessThan(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("slaDuration override: setting variable to PT5M uses that value instead of default")
    void slaDuration_override_usesProvidedValue() {
        Map<String, Object> vars = new HashMap<>(SICK_LEAVE);
        vars.put("slaDuration", "PT5M");

        ProcessInstance pi = runtimeService.startProcessInstanceByKey("leave-request", vars);
        String pid = pi.getId();

        Job timerJob = managementService.createTimerJobQuery()
                .processInstanceId(pid)
                .singleResult();
        assertThat(timerJob).as("timer job must exist when slaDuration override is set").isNotNull();

        // Due date should be approximately now + 5 minutes (within 30 seconds tolerance).
        Instant expectedDue = Instant.now().plus(Duration.ofMinutes(5));
        Instant actualDue = timerJob.getDuedate().toInstant();
        assertThat(Duration.between(actualDue, expectedDue).abs())
                .as("timer due date should be ~5 min from now (PT5M override)")
                .isLessThan(Duration.ofSeconds(30));
    }

    // ── it-helpdesk-ticket: resolveTicket non-interrupting SLA timer ───────────

    @Test
    @DisplayName("it-helpdesk-ticket: resolveTicket has non-interrupting SLA timer boundary routing to endEventSlaBreached")
    void itHelpdesk_resolveTicket_hasNonInterruptingSlaTimerBoundary() {
        // The process uses ${notificationDelegate} in two sendTasks; supply a no-op stub
        // so the process can reach resolveTicket without a Spring context.
        JavaDelegate noOpDelegate = execution -> {};
        WerkflowTestProcessEngine localEngine = WerkflowTestProcessEngine.build(
                "helpdeskSlaTimer",
                Map.of("notificationDelegate", noOpDelegate));
        try {
            new ProcessTestDsl(localEngine.getProcessEngine()).deploy(DESK_BPMN);

            RuntimeService localRt      = localEngine.getProcessEngine().getRuntimeService();
            ManagementService localMgmt = localEngine.getProcessEngine().getManagementService();
            TaskService localTask       = localEngine.getProcessEngine().getTaskService();
            HistoryService localHistory = localEngine.getProcessEngine().getHistoryService();

            ProcessInstance pi = localRt.startProcessInstanceByKey("it-helpdesk-ticket",
                    Map.of("requesterEmail", "test@example.com",
                           "subject", "Test ticket",
                           "description", "desc",
                           "category", "software",
                           "priority", "medium"));
            String pid = pi.getId();

            // The two sendTasks complete synchronously; resolveTicket must now be the active task.
            Task resolveTask = localTask.createTaskQuery()
                    .processInstanceId(pid)
                    .taskDefinitionKey("resolveTicket")
                    .singleResult();
            assertThat(resolveTask)
                    .as("resolveTicket task must be active after acknowledgeTicket sendTask completes")
                    .isNotNull();

            // The non-interrupting SLA timer boundary must have registered a timer job.
            Job timerJob = localMgmt.createTimerJobQuery()
                    .processInstanceId(pid)
                    .singleResult();
            assertThat(timerJob)
                    .as("SLA timer job must exist on resolveTicket boundary")
                    .isNotNull();

            // Fire the timer synthetically — the task must NOT be cancelled (non-interrupting).
            Job executableJob = localMgmt.moveTimerToExecutableJob(timerJob.getId());
            localMgmt.executeJob(executableJob.getId());

            // endEventSlaBreached must appear exactly once in history.
            List<HistoricActivityInstance> slaActivities = localHistory
                    .createHistoricActivityInstanceQuery()
                    .processInstanceId(pid)
                    .activityId("endEventSlaBreached")
                    .list();
            assertThat(slaActivities)
                    .as("endEventSlaBreached must be recorded after SLA timer fires on resolveTicket")
                    .hasSize(1);

            // resolveTicket must still be active — the timer is non-interrupting.
            Task stillActive = localTask.createTaskQuery()
                    .processInstanceId(pid)
                    .taskDefinitionKey("resolveTicket")
                    .singleResult();
            assertThat(stillActive)
                    .as("resolveTicket must still be active after non-interrupting timer fires")
                    .isNotNull();
        } finally {
            localEngine.close();
        }
    }
}
