package com.werkflow.engine.testsupport;

import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;

import java.util.Map;

/**
 * Fluent DSL for Werkflow Scope-2 process tests (ADR-028).
 *
 * <p>Wraps the Flowable engine services with ergonomic helpers for deploying BPMNs and
 * starting process instances. {@link #start} returns a {@link ProcessHandle} scoped to the
 * new instance — all assertions operate on that handle, not on shared DSL state.
 *
 * <p>Usage:
 * <pre>{@code
 * dsl.deploy("fragments/my-process.bpmn20.xml");
 * dsl.start("my-process", Map.of("amount", 1000))
 *    .assertCompleted()
 *    .assertHistoricVariable("result", "approved");
 * }</pre>
 */
public class ProcessTestDsl {

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final RepositoryService repositoryService;

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

    /**
     * Starts a process instance by key with the given variables and returns a {@link ProcessHandle}
     * scoped to that instance. Each call creates an independent handle — multiple handles from the
     * same DSL coexist safely within one test class.
     */
    public ProcessHandle start(String processDefinitionKey, Map<String, Object> vars) {
        String id = runtimeService
            .startProcessInstanceByKey(processDefinitionKey, vars)
            .getId();
        return new ProcessHandle(id, runtimeService, taskService, historyService);
    }
}
