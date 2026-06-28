package com.werkflow.engine.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.job.api.DeadLetterJobQuery;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FlowableMetricsBinder}.
 * Uses {@link SimpleMeterRegistry} to verify gauge registration without a full Spring context.
 * Stubs are scoped to each test to avoid strict-stubbing false positives: Micrometer gauge
 * lambdas are lazy — a lambda is only invoked when its gauge is polled, not at registration time.
 */
@ExtendWith(MockitoExtension.class)
class FlowableMetricsBinderTest {

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private TaskService taskService;

    @Mock
    private ManagementService managementService;

    @Mock
    private ProcessInstanceQuery processInstanceQuery;

    @Mock
    private TaskQuery taskQuery;

    @Mock
    private DeadLetterJobQuery deadLetterJobQuery;

    private FlowableMetricsBinder binder;

    @BeforeEach
    void setUp() {
        binder = new FlowableMetricsBinder(runtimeService, taskService, managementService);
    }

    @Test
    @DisplayName("bindTo registers werkflow.flowable.process.instances.active gauge reporting active count")
    void bindTo_registersActiveProcessInstancesGauge() {
        when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.active()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.count()).thenReturn(3L);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        binder.bindTo(registry);

        double value = registry.get("werkflow.flowable.process.instances.active").gauge().value();
        assertThat(value).isEqualTo(3.0);
    }

    @Test
    @DisplayName("bindTo registers werkflow.flowable.tasks.open gauge reporting open task count")
    void bindTo_registersOpenTasksGauge() {
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.count()).thenReturn(5L);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        binder.bindTo(registry);

        double value = registry.get("werkflow.flowable.tasks.open").gauge().value();
        assertThat(value).isEqualTo(5.0);
    }

    @Test
    @DisplayName("bindTo registers werkflow.flowable.jobs.deadletter gauge reporting dead-letter job count")
    void bindTo_registersDeadLetterJobsGauge() {
        when(managementService.createDeadLetterJobQuery()).thenReturn(deadLetterJobQuery);
        when(deadLetterJobQuery.count()).thenReturn(2L);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        binder.bindTo(registry);

        double value = registry.get("werkflow.flowable.jobs.deadletter").gauge().value();
        assertThat(value).isEqualTo(2.0);
    }
}
