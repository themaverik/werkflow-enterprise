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
 * DoD test for F-EV-8: proves the four VERIFIED-SUPPORTED event types in Flowable 7.2
 * actually EXECUTE, not merely deploy. One test per event type.
 *
 * <p>Event support matrix (verified in M4.13 audit):
 * <ul>
 *   <li><b>terminate</b> — FULL support; ends the current scope.</li>
 *   <li><b>escalation</b> — FULL support; caught by a boundary escalation event on a sub-process.</li>
 *   <li><b>compensation</b> — FULL support; compensation boundary triggers when compensate throw fires.</li>
 *   <li><b>conditional</b> — Supported but REACTIVE: evaluates when process variables are written;
 *       no polling. Triggered via {@code RuntimeService.evaluateConditionalEvents()} (confirmed
 *       against Flowable 7.2 RuntimeService bytecode — see the method signature).</li>
 * </ul>
 *
 * <p>Uses {@link WerkflowTestProcessEngine} (same parse handlers + validators as production).
 * No Spring context, no Postgres, no Vault.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("F-EV-8: supported event types (terminate/escalation/compensation/conditional) — deploy + execute")
class EventTypeExecutionTest {

    // -------------------------------------------------------------------------
    // BPMN: terminate end event
    //   start → userTask (park) → endEvent(terminate)
    //   Proves: a terminate end event ends the process instance immediately.
    // -------------------------------------------------------------------------
    private static final String TERMINATE_BPMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:flowable="http://flowable.org/bpmn"
                     targetNamespace="http://werkflow.com/bpmn/test">
          <process id="terminate-proc" isExecutable="true">
            <startEvent id="start"/>
            <sequenceFlow id="f1" sourceRef="start" targetRef="task"/>
            <userTask id="task" name="Review"/>
            <sequenceFlow id="f2" sourceRef="task" targetRef="term"/>
            <endEvent id="term">
              <terminateEventDefinition flowable:terminateAll="true"/>
            </endEvent>
          </process>
        </definitions>
        """;

    // -------------------------------------------------------------------------
    // BPMN: escalation boundary event
    //   start → subProcess [ start → escalationThrow → end ]
    //                 ↑ boundary escalation catch → park → end
    //   Proves: escalation thrown inside sub-process is caught by boundary event;
    //   the escalation-handling path (park task) runs.
    // -------------------------------------------------------------------------
    private static final String ESCALATION_BPMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     targetNamespace="http://werkflow.com/bpmn/test">
          <escalation id="esc1" name="ReviewEscalation" escalationCode="ESC-001"/>
          <process id="escalation-proc" isExecutable="true">
            <startEvent id="start"/>
            <sequenceFlow id="f1" sourceRef="start" targetRef="sub"/>
            <subProcess id="sub" name="SubProcess">
              <startEvent id="subStart"/>
              <sequenceFlow id="sf1" sourceRef="subStart" targetRef="escalThrow"/>
              <intermediateThrowEvent id="escalThrow" name="ThrowEscalation">
                <escalationEventDefinition escalationRef="esc1"/>
              </intermediateThrowEvent>
              <sequenceFlow id="sf2" sourceRef="escalThrow" targetRef="subEnd"/>
              <endEvent id="subEnd"/>
            </subProcess>
            <boundaryEvent id="escalBoundary" attachedToRef="sub" cancelActivity="false">
              <escalationEventDefinition escalationRef="esc1"/>
            </boundaryEvent>
            <sequenceFlow id="f2" sourceRef="escalBoundary" targetRef="park"/>
            <userTask id="park" name="HandleEscalation"/>
            <sequenceFlow id="f3" sourceRef="park" targetRef="end"/>
            <sequenceFlow id="f4" sourceRef="sub" targetRef="end"/>
            <endEvent id="end"/>
          </process>
        </definitions>
        """;

    // -------------------------------------------------------------------------
    // BPMN: compensation
    //   start → book(ServiceTask / pass-through) → park(UserTask) → compThrow → end
    //   compBoundary (compensation boundary on book) —association—→ cancel(UserTask, isForCompensation)
    //
    //   Flowable 7.2 compensation model (empirically determined):
    //   - The bounded activity must be an automatic activity (service task, receive task, etc.)
    //     to get a compensation subscription created on completion. A UserTask does not create
    //     a CompensateEventSubscriptionEntity on its own; Flowable only tracks compensation for
    //     activities that pass through the automatic leave() path.
    //   - The compensation handler (cancel) is connected via BPMN <association>, not a sequenceFlow.
    //   - The compensate throw/end event can be an intermediateThrowEvent or endEvent with
    //     compensateEventDefinition. Using waitForCompletion="true" is the synchronous form.
    //   - We use a receiveTask as the "book" activity — Flowable automatically triggers its
    //     leave path when runtimeService.trigger() is called, which records the compensation
    //     subscription. After that, a park UserTask allows us to verify then trigger the
    //     compensate throw event.
    //
    //   NOTE: The compensation handler (cancel) is a ManualTask (pass-through in Flowable 7.x),
    //   which completes immediately, allowing us to verify it ran via history.
    // -------------------------------------------------------------------------
    private static final String COMPENSATION_BPMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     targetNamespace="http://werkflow.com/bpmn/test">
          <process id="compensation-proc" isExecutable="true">
            <startEvent id="start"/>
            <sequenceFlow id="f1" sourceRef="start" targetRef="book"/>
            <receiveTask id="book" name="BookReservation"/>
            <sequenceFlow id="f2" sourceRef="book" targetRef="park"/>
            <userTask id="park" name="Review"/>
            <sequenceFlow id="f3" sourceRef="park" targetRef="compThrow"/>
            <intermediateThrowEvent id="compThrow" name="TriggerCompensation">
              <compensateEventDefinition waitForCompletion="true"/>
            </intermediateThrowEvent>
            <sequenceFlow id="f4" sourceRef="compThrow" targetRef="end"/>
            <endEvent id="end"/>
            <boundaryEvent id="compBoundary" attachedToRef="book">
              <compensateEventDefinition/>
            </boundaryEvent>
            <manualTask id="cancel" name="CancelReservation" isForCompensation="true"/>
            <association id="assoc1" sourceRef="compBoundary" targetRef="cancel"/>
          </process>
        </definitions>
        """;

    // -------------------------------------------------------------------------
    // BPMN: conditional intermediate catch event
    //   start → conditionalCatch (condition: ${approved == true}) → park → end
    //   Proves: the event is re-evaluated when variables are written via
    //   RuntimeService.evaluateConditionalEvents(), and the instance advances
    //   when the condition is satisfied.
    // -------------------------------------------------------------------------
    private static final String CONDITIONAL_BPMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     targetNamespace="http://werkflow.com/bpmn/test">
          <process id="conditional-proc" isExecutable="true">
            <startEvent id="start"/>
            <sequenceFlow id="f1" sourceRef="start" targetRef="condCatch"/>
            <intermediateCatchEvent id="condCatch" name="WaitForApproval">
              <conditionalEventDefinition>
                <condition><![CDATA[${approved == true}]]></condition>
              </conditionalEventDefinition>
            </intermediateCatchEvent>
            <sequenceFlow id="f2" sourceRef="condCatch" targetRef="park"/>
            <userTask id="park" name="PostApproval"/>
            <sequenceFlow id="f3" sourceRef="park" targetRef="end"/>
            <endEvent id="end"/>
          </process>
        </definitions>
        """;

    private WerkflowTestProcessEngine testEngine;
    private RepositoryService repositoryService;
    private RuntimeService runtimeService;
    private TaskService taskService;
    private HistoryService historyService;

    @BeforeAll
    void bootEngine() {
        testEngine = WerkflowTestProcessEngine.build("eventTypeExecution");
        repositoryService = testEngine.getProcessEngine().getRepositoryService();
        runtimeService   = testEngine.getProcessEngine().getRuntimeService();
        taskService      = testEngine.getProcessEngine().getTaskService();
        historyService   = testEngine.getProcessEngine().getHistoryService();

        // Deploy all 4 processes once.
        deploy("terminate-proc",    TERMINATE_BPMN);
        deploy("escalation-proc",   ESCALATION_BPMN);
        deploy("compensation-proc", COMPENSATION_BPMN);
        deploy("conditional-proc",  CONDITIONAL_BPMN);
    }

    @AfterAll
    void shutdown() {
        if (testEngine != null) {
            testEngine.close();
        }
    }

    private void deploy(String name, String xml) {
        repositoryService.createDeployment()
                .addString(name + ".bpmn20.xml", xml)
                .name(name)
                .deploy();
    }

    // -------------------------------------------------------------------------
    // Test 1: terminate
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("terminate: completing the user task routes to terminateEndEvent and ends the instance")
    void terminate_instanceEndsAfterTerminateEndEvent() {
        ProcessInstance pi = runtimeService.startProcessInstanceByKey("terminate-proc");

        // Instance is active; it parks at the user task.
        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(pi.getId()).count())
                .as("instance should be active before task completion")
                .isEqualTo(1);

        Task task = taskService.createTaskQuery()
                .processInstanceId(pi.getId()).taskDefinitionKey("task").singleResult();
        assertThat(task).as("user task must be present").isNotNull();

        // Complete the task — execution flows to the terminateEndEvent.
        taskService.complete(task.getId());

        // Instance must have ended.
        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(pi.getId()).count())
                .as("instance must be terminated after terminateEndEvent")
                .isEqualTo(0);

        // Confirm the terminate end event actually executed in history.
        List<HistoricActivityInstance> termActs = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(pi.getId())
                .activityId("term")
                .finished()
                .list();
        assertThat(termActs)
                .as("terminateEndEvent activity must appear as finished in history")
                .hasSize(1);
    }

    // -------------------------------------------------------------------------
    // Test 2: escalation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("escalation: throw inside sub-process is caught by boundary; handling path runs")
    void escalation_boundaryHandlerRuns() {
        ProcessInstance pi = runtimeService.startProcessInstanceByKey("escalation-proc");

        // The sub-process immediately throws the escalation; the boundary catch fires
        // and routes to the 'park' user task. The sub-process also continues (non-interrupting boundary).
        Task parkTask = taskService.createTaskQuery()
                .processInstanceId(pi.getId()).taskDefinitionKey("park").singleResult();
        assertThat(parkTask)
                .as("escalation boundary handler path: 'park' userTask must be reached")
                .isNotNull();

        // Verify the escalation throw event ran in history.
        List<HistoricActivityInstance> escalActs = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(pi.getId())
                .activityId("escalThrow")
                .finished()
                .list();
        assertThat(escalActs)
                .as("escalation throw event must appear as finished in history")
                .hasSize(1);
    }

    // -------------------------------------------------------------------------
    // Test 3: compensation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("compensation: trigger() on receiveTask records subscription; compensate throw runs the handler")
    void compensation_handlerRunsAfterCompensateThrow() {
        ProcessInstance pi = runtimeService.startProcessInstanceByKey("compensation-proc");

        // Step 1: trigger the receiveTask (book) to complete it — this creates the compensation
        // subscription for 'book' in Flowable's event subscription table.
        String bookExecId = runtimeService.createExecutionQuery()
                .processInstanceId(pi.getId()).activityId("book").singleResult().getId();
        runtimeService.trigger(bookExecId);

        // Step 2: execution advances to the park userTask. Verify the 'book' receiveTask completed.
        Task parkTask = taskService.createTaskQuery()
                .processInstanceId(pi.getId()).taskDefinitionKey("park").singleResult();
        assertThat(parkTask).as("'park' user task must be reached after book receiveTask completes").isNotNull();

        // Step 3: complete the park task — execution flows to the compensate intermediate throw event.
        taskService.complete(parkTask.getId());

        // After the compensate throw: the compensation handler (cancel serviceTask) fires synchronously,
        // then execution continues to the end event, and the process ends.
        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(pi.getId()).count())
                .as("instance must end after compensation handler completes and process reaches end event")
                .isEqualTo(0);

        // The 'cancel' serviceTask must appear in history — proving the compensation handler executed.
        List<HistoricActivityInstance> cancelActs = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(pi.getId())
                .activityId("cancel")
                .finished()
                .list();
        assertThat(cancelActs)
                .as("compensation handler 'cancel' must appear in history — proving compensation executed")
                .hasSize(1);
    }

    // -------------------------------------------------------------------------
    // Test 4: conditional
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("conditional: instance waits at conditional catch; advances when variable satisfies condition")
    void conditional_advancesWhenVariableSatisfiesCondition() {
        ProcessInstance pi = runtimeService.startProcessInstanceByKey("conditional-proc");

        // Instance parks at the conditional catch event — condition not yet met.
        Task parkBefore = taskService.createTaskQuery()
                .processInstanceId(pi.getId()).taskDefinitionKey("park").singleResult();
        assertThat(parkBefore)
                .as("instance must NOT yet be at 'park' — condition is unsatisfied")
                .isNull();

        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(pi.getId()).count())
                .as("instance must still be active (waiting at conditional catch)")
                .isEqualTo(1);

        // Set the variable that satisfies the condition and re-evaluate.
        // RuntimeService.evaluateConditionalEvents(processInstanceId, variables) is the
        // verified Flowable 7.2 API for reactive conditional event evaluation.
        runtimeService.evaluateConditionalEvents(pi.getId(), Map.of("approved", true));

        // Now the instance should have advanced past the conditional catch to the park task.
        Task parkAfter = taskService.createTaskQuery()
                .processInstanceId(pi.getId()).taskDefinitionKey("park").singleResult();
        assertThat(parkAfter)
                .as("instance must advance to 'park' userTask after condition is satisfied")
                .isNotNull();
    }
}
