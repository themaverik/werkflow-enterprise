package com.werkflow.engine.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Component;

/**
 * Registers Flowable-specific operational gauges with the Micrometer {@link MeterRegistry}.
 *
 * <p>Spring Boot auto-discovers all {@link MeterBinder} beans and calls
 * {@link #bindTo(MeterRegistry)} during actuator initialisation. No explicit wiring is needed.
 *
 * <p>Gauges exposed (all are instantaneous counts sampled at scrape time):
 * <ul>
 *   <li>{@code werkflow.flowable.process.instances.active} — running process instances</li>
 *   <li>{@code werkflow.flowable.tasks.open} — uncompleted user/service tasks</li>
 *   <li>{@code werkflow.flowable.jobs.deadletter} — jobs in the dead-letter queue</li>
 * </ul>
 *
 * <p>See ADR-036 (Observability Phase 1).
 */
@Component
public class FlowableMetricsBinder implements MeterBinder {

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final ManagementService managementService;

    public FlowableMetricsBinder(
            RuntimeService runtimeService,
            TaskService taskService,
            ManagementService managementService) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.managementService = managementService;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(
                        "werkflow.flowable.process.instances.active",
                        runtimeService,
                        rs -> (double) rs.createProcessInstanceQuery().active().count())
                .description("Number of currently active Flowable process instances")
                .register(registry);

        // act_ru_task holds only open tasks (completed tasks move to history), so an
        // unfiltered count() is the open-task total — do not add a state filter here.
        Gauge.builder(
                        "werkflow.flowable.tasks.open",
                        taskService,
                        ts -> (double) ts.createTaskQuery().count())
                .description("Number of open (uncompleted) Flowable tasks")
                .register(registry);

        Gauge.builder(
                        "werkflow.flowable.jobs.deadletter",
                        managementService,
                        ms -> (double) ms.createDeadLetterJobQuery().count())
                .description("Number of jobs in the Flowable dead-letter queue")
                .register(registry);
    }
}
