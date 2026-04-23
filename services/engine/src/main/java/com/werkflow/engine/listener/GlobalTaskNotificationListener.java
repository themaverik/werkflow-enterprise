package com.werkflow.engine.listener;

import com.werkflow.engine.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEntityEvent;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Global Flowable event listener that sends email notifications automatically
 * on task assignment and human task completion — no NOTIFICATION nodes needed in BPMN.
 *
 * Events handled:
 * - TASK_ASSIGNED  → email the assignee ("you have a new task")
 * - TASK_COMPLETED → email the process initiator ("your request has progressed")
 *                    skipped when assignee == initiator (self-submission tasks)
 *
 * Registered in FlowableConfig via setTypedEventListeners.
 * isFailOnException = false — email failures never abort workflows.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GlobalTaskNotificationListener implements FlowableEventListener {

    private final NotificationService notificationService;
    private final UserEmailResolver emailResolver;
    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;

    @Override
    public void onEvent(FlowableEvent event) {
        if (!(event instanceof FlowableEngineEntityEvent entityEvent)) {
            return;
        }
        if (!(entityEvent.getEntity() instanceof TaskEntity task)) {
            return;
        }

        FlowableEngineEventType type = (FlowableEngineEventType) event.getType();

        switch (type) {
            case TASK_ASSIGNED  -> handleTaskAssigned(task);
            case TASK_COMPLETED -> handleTaskCompleted(task);
            default             -> { /* not handled */ }
        }
    }

    private void handleTaskAssigned(TaskEntity task) {
        String assignee = task.getAssignee();
        if (assignee == null || assignee.isBlank()) {
            return;
        }

        String taskName    = taskName(task);
        String processName = processName(task.getProcessDefinitionId());

        emailResolver.resolveEmail(assignee).ifPresentOrElse(
            email -> {
                log.info("TASK_ASSIGNED — notifying assignee='{}' task='{}'", assignee, taskName);
                notificationService.sendTaskAssignedNotification(task.getId(), email, taskName, processName);
            },
            () -> log.debug("TASK_ASSIGNED — no email for assignee='{}'", assignee)
        );
    }

    private void handleTaskCompleted(TaskEntity task) {
        String assignee      = task.getAssignee();
        String processInstId = task.getProcessInstanceId();
        String startUserId   = resolveStartUserId(processInstId);

        // Skip notification when the person completing the task is also the process initiator
        // (covers "Submit Request" user tasks where requester fills their own form)
        if (startUserId == null || Objects.equals(assignee, startUserId)) {
            log.debug("TASK_COMPLETED — skipping self-completion task='{}'", task.getId());
            return;
        }

        String taskName    = taskName(task);
        String processName = processName(task.getProcessDefinitionId());
        String completedBy = assignee != null ? assignee : "System";

        emailResolver.resolveEmail(startUserId).ifPresentOrElse(
            email -> {
                log.info("TASK_COMPLETED — notifying initiator='{}' task='{}'", startUserId, taskName);
                notificationService.sendTaskCompletedNotification(
                    task.getId(),
                    List.of(email),
                    "completed",
                    "",
                    taskName,
                    processName,
                    completedBy,
                    processInstId
                );
            },
            () -> log.debug("TASK_COMPLETED — no email for initiator='{}'", startUserId)
        );
    }

    private String resolveStartUserId(String processInstanceId) {
        try {
            ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
            return pi != null ? pi.getStartUserId() : null;
        } catch (Exception e) {
            log.warn("could not resolve startUserId for pi={}: {}", processInstanceId, e.getMessage());
            return null;
        }
    }

    private String processName(String processDefinitionId) {
        if (processDefinitionId == null) return "Workflow";
        try {
            ProcessDefinition def = repositoryService.getProcessDefinition(processDefinitionId);
            return def != null && def.getName() != null ? def.getName() : "Workflow";
        } catch (Exception e) {
            return "Workflow";
        }
    }

    private String taskName(TaskEntity task) {
        return task.getName() != null ? task.getName() : task.getId();
    }

    @Override
    public boolean isFailOnException() {
        return false;
    }

    @Override
    public boolean isFireOnTransactionLifecycleEvent() {
        return false;
    }

    @Override
    public String getOnTransaction() {
        return null;
    }
}
