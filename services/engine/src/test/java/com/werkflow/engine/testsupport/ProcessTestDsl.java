package com.werkflow.engine.testsupport;

import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.flowable.task.api.Task;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fluent DSL for Werkflow Scope-2 process tests (ADR-028).
 *
 * <p>Wraps the Flowable engine services with ergonomic helpers for deploying BPMNs, driving
 * process instances step by step, and asserting outputs — replacing per-test boilerplate.
 *
 * <p>Stateful per {@link #start} call: each call to {@code start} sets the current process
 * instance ID, and subsequent assertions operate on that instance.
 */
public class ProcessTestDsl {

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final RepositoryService repositoryService;

    private String processInstanceId;

    public ProcessTestDsl(ProcessEngine engine) {
        this.runtimeService    = engine.getRuntimeService();
        this.taskService       = engine.getTaskService();
        this.historyService    = engine.getHistoryService();
        this.repositoryService = engine.getRepositoryService();
    }

    /** Deploys a BPMN or DMN resource from the test classpath. */
    public ProcessTestDsl deploy(String classpathResource) {
        repositoryService.createDeployment()
            .addClasspathResource(classpathResource)
            .deploy();
        return this;
    }

    /** Starts a process instance by key with the given variables. */
    public ProcessTestDsl start(String processDefinitionKey, Map<String, Object> vars) {
        processInstanceId = runtimeService
            .startProcessInstanceByKey(processDefinitionKey, vars)
            .getId();
        return this;
    }

    /** Completes the current user task matching {@code taskDefinitionKey} with the given variables. */
    public ProcessTestDsl completeTask(String taskDefinitionKey, Map<String, Object> vars) {
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
    public ProcessTestDsl assertWaitingAt(String activityId) {
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
    public ProcessTestDsl assertCompleted() {
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
    public ProcessTestDsl assertVariable(String name, Object expected) {
        Object actual = runtimeService.getVariable(processInstanceId, name);
        assertThat(actual).as("Process variable '%s'", name).isEqualTo(expected);
        return this;
    }

    /** Asserts a historic variable (available after process completion) equals {@code expected}. */
    public ProcessTestDsl assertHistoricVariable(String name, Object expected) {
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
