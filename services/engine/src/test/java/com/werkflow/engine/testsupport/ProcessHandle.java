package com.werkflow.engine.testsupport;

import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.variable.api.history.HistoricVariableInstance;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scoped handle to a single process instance, returned by {@link ProcessTestDsl#start}.
 *
 * <p>All assertion and interaction methods operate on the specific instance captured at
 * creation time — there is no shared mutable state. Multiple handles from the same
 * {@link ProcessTestDsl} coexist safely within one test class.
 */
public final class ProcessHandle {

    private final String processInstanceId;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;

    ProcessHandle(String processInstanceId,
                  RuntimeService runtimeService,
                  TaskService taskService,
                  HistoryService historyService) {
        this.processInstanceId = processInstanceId;
        this.runtimeService   = runtimeService;
        this.taskService      = taskService;
        this.historyService   = historyService;
    }

    /** Completes the current user task matching {@code taskDefinitionKey} with the given variables. */
    public ProcessHandle completeTask(String taskDefinitionKey, Map<String, Object> vars) {
        Task task = taskService.createTaskQuery()
            .processInstanceId(processInstanceId)
            .taskDefinitionKey(taskDefinitionKey)
            .singleResult();
        assertThat(task)
            .as("Expected active task with definition key '%s' in process %s",
                taskDefinitionKey, processInstanceId)
            .isNotNull();
        taskService.complete(task.getId(), vars);
        return this;
    }

    /** Asserts the process instance is currently waiting at the given activity. */
    public ProcessHandle assertWaitingAt(String activityId) {
        long count = runtimeService.createExecutionQuery()
            .processInstanceId(processInstanceId)
            .activityId(activityId)
            .count();
        assertThat(count)
            .as("Expected process %s to be waiting at activity '%s'", processInstanceId, activityId)
            .isGreaterThan(0);
        return this;
    }

    /** Asserts the process instance has completed (is in history as finished). */
    public ProcessHandle assertCompleted() {
        long count = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .finished()
            .count();
        assertThat(count)
            .as("Expected process instance %s to be completed", processInstanceId)
            .isEqualTo(1);
        return this;
    }

    /** Asserts a live process variable equals {@code expected}. Use only while the process is still active. */
    public ProcessHandle assertVariable(String name, Object expected) {
        Object actual = runtimeService.getVariable(processInstanceId, name);
        assertThat(actual).as("Process variable '%s'", name).isEqualTo(expected);
        return this;
    }

    /** Asserts a historic variable (available after process completion) equals {@code expected}. */
    public ProcessHandle assertHistoricVariable(String name, Object expected) {
        HistoricVariableInstance var = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(processInstanceId)
            .variableName(name)
            .singleResult();
        assertThat(var)
            .as("Historic variable '%s' not found in process %s", name, processInstanceId)
            .isNotNull();
        assertThat(var.getValue()).as("Historic variable '%s'", name).isEqualTo(expected);
        return this;
    }

    /** Returns the value of a historic variable, or {@code null} if not found. */
    public Object historicVariable(String name) {
        HistoricVariableInstance var = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(processInstanceId)
            .variableName(name)
            .singleResult();
        return var != null ? var.getValue() : null;
    }
}
