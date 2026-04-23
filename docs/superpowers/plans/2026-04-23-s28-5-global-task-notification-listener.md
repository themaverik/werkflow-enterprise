# S28.5 Global Task Notification Listener — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Register a global Flowable engine event listener that automatically sends email notifications on every task assignment and every human task completion — eliminating the need for explicit NOTIFICATION service task nodes in BPMN diagrams.

**Architecture:** A `GlobalTaskNotificationListener` implements `FlowableEventListener` and is registered in `FlowableConfig` for `TASK_ASSIGNED` and `TASK_COMPLETED` event types. A `UserEmailResolver` resolves Keycloak usernames to email addresses with an in-memory cache. On task assignment the assignee is notified; on task completion the process initiator is notified (skipped when the same user completes their own submission task). The existing `NotificationService` (async, retry-enabled) handles all actual SMTP delivery.

**Tech Stack:** Java 21, Spring Boot 3, Flowable 7, Keycloak Admin Client, JUnit 5, Mockito

---

## File Map

| Action | Path |
|--------|------|
| Create | `services/engine/src/main/java/com/werkflow/engine/listener/UserEmailResolver.java` |
| Create | `services/engine/src/main/java/com/werkflow/engine/listener/GlobalTaskNotificationListener.java` |
| Modify | `services/engine/src/main/java/com/werkflow/engine/config/FlowableConfig.java` |
| Create | `services/engine/src/test/java/com/werkflow/engine/listener/UserEmailResolverTest.java` |
| Create | `services/engine/src/test/java/com/werkflow/engine/listener/GlobalTaskNotificationListenerTest.java` |

---

## Task 1: UserEmailResolver — test

**Files:**
- Create: `services/engine/src/test/java/com/werkflow/engine/listener/UserEmailResolverTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.werkflow.engine.listener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class UserEmailResolverTest {

    private Keycloak keycloak;
    private RealmResource realmResource;
    private UsersResource usersResource;
    private UserEmailResolver resolver;

    @BeforeEach
    void setUp() {
        keycloak = mock(Keycloak.class);
        realmResource = mock(RealmResource.class);
        usersResource = mock(UsersResource.class);
        when(keycloak.realm("werkflow")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        resolver = new UserEmailResolver(keycloak, "werkflow");
    }

    @Test
    void resolveEmail_returnsEmail_whenUserExists() {
        UserRepresentation user = new UserRepresentation();
        user.setEmail("jane.employee@werkflow.local");
        when(usersResource.searchByUsername("jane.employee", true)).thenReturn(List.of(user));

        Optional<String> result = resolver.resolveEmail("jane.employee");

        assertThat(result).contains("jane.employee@werkflow.local");
    }

    @Test
    void resolveEmail_returnsEmpty_whenUserNotFound() {
        when(usersResource.searchByUsername("unknown", true)).thenReturn(List.of());

        Optional<String> result = resolver.resolveEmail("unknown");

        assertThat(result).isEmpty();
    }

    @Test
    void resolveEmail_returnsEmpty_whenUsernameIsNull() {
        Optional<String> result = resolver.resolveEmail(null);

        assertThat(result).isEmpty();
        verifyNoInteractions(usersResource);
    }

    @Test
    void resolveEmail_cachesResult_andDoesNotCallKeycloakTwice() {
        UserRepresentation user = new UserRepresentation();
        user.setEmail("jane.employee@werkflow.local");
        when(usersResource.searchByUsername("jane.employee", true)).thenReturn(List.of(user));

        resolver.resolveEmail("jane.employee");
        resolver.resolveEmail("jane.employee");

        verify(usersResource, times(1)).searchByUsername("jane.employee", true);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd services/engine
./mvnw test -pl . -Dtest=UserEmailResolverTest -q 2>&1 | tail -10
```

Expected: FAIL — `UserEmailResolver` class not found.

---

## Task 2: UserEmailResolver — implementation

**Files:**
- Create: `services/engine/src/main/java/com/werkflow/engine/listener/UserEmailResolver.java`

- [ ] **Step 3: Implement UserEmailResolver**

```java
package com.werkflow.engine.listener;

import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a Keycloak username to an email address.
 * Results are cached in-memory for the lifetime of the application.
 */
@Slf4j
@Component
public class UserEmailResolver {

    private final Keycloak keycloak;
    private final String realm;
    private final ConcurrentHashMap<String, Optional<String>> cache = new ConcurrentHashMap<>();

    public UserEmailResolver(
        Keycloak keycloak,
        @Value("${keycloak.realm:werkflow}") String realm
    ) {
        this.keycloak = keycloak;
        this.realm = realm;
    }

    /**
     * Resolves a username to an email address using Keycloak's exact-match search.
     * Returns {@link Optional#empty()} if the username is null/blank or not found.
     * Results are cached — Keycloak is called at most once per username per process lifetime.
     */
    public Optional<String> resolveEmail(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return cache.computeIfAbsent(username, this::lookupEmail);
    }

    private Optional<String> lookupEmail(String username) {
        try {
            List<UserRepresentation> users =
                keycloak.realm(realm).users().searchByUsername(username, true);
            if (users.isEmpty()) {
                log.debug("UserEmailResolver: no Keycloak user found for username='{}'", username);
                return Optional.empty();
            }
            String email = users.get(0).getEmail();
            log.debug("UserEmailResolver: resolved username='{}' → email='{}'", username, email);
            return Optional.ofNullable(email);
        } catch (Exception e) {
            log.warn("UserEmailResolver: failed to resolve email for username='{}': {}", username, e.getMessage());
            return Optional.empty();
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd services/engine
./mvnw test -pl . -Dtest=UserEmailResolverTest -q 2>&1 | tail -10
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
cd services/engine
git add src/main/java/com/werkflow/engine/listener/UserEmailResolver.java \
        src/test/java/com/werkflow/engine/listener/UserEmailResolverTest.java
git commit -m "feat(engine): add UserEmailResolver — username-to-email Keycloak lookup with cache"
```

---

## Task 3: GlobalTaskNotificationListener — test

**Files:**
- Create: `services/engine/src/test/java/com/werkflow/engine/listener/GlobalTaskNotificationListenerTest.java`

- [ ] **Step 6: Write the failing test**

```java
package com.werkflow.engine.listener;

import com.werkflow.engine.service.NotificationService;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.engine.delegate.event.impl.FlowableEntityEventImpl;
import org.flowable.task.service.impl.persistence.entity.TaskEntityImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GlobalTaskNotificationListenerTest {

    private NotificationService notificationService;
    private UserEmailResolver emailResolver;
    private RuntimeService runtimeService;
    private RepositoryService repositoryService;
    private GlobalTaskNotificationListener listener;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        emailResolver = mock(UserEmailResolver.class);
        runtimeService = mock(RuntimeService.class);
        repositoryService = mock(RepositoryService.class);
        listener = new GlobalTaskNotificationListener(
            notificationService, emailResolver, runtimeService, repositoryService
        );
    }

    @Test
    void onTaskAssigned_sendsEmailToAssignee() {
        TaskEntityImpl task = new TaskEntityImpl();
        task.setId("task-1");
        task.setName("Manager Approval");
        task.setAssignee("john.manager");
        task.setProcessInstanceId("pi-1");
        task.setProcessDefinitionId("def-1");

        mockProcessInstance("pi-1", "jane.employee");
        mockProcessDefinition("def-1", "Leave Request");
        when(emailResolver.resolveEmail("john.manager")).thenReturn(Optional.of("john.manager@werkflow.local"));

        FlowableEntityEventImpl event = new FlowableEntityEventImpl(task, FlowableEngineEventType.TASK_ASSIGNED);
        listener.onEvent(event);

        verify(notificationService).sendTaskAssignedNotification(
            eq("task-1"),
            eq("john.manager@werkflow.local"),
            eq("Manager Approval"),
            eq("Leave Request")
        );
    }

    @Test
    void onTaskAssigned_skipsEmail_whenAssigneeEmailNotFound() {
        TaskEntityImpl task = new TaskEntityImpl();
        task.setId("task-2");
        task.setAssignee("nobody");
        task.setProcessInstanceId("pi-2");
        task.setProcessDefinitionId("def-1");

        mockProcessInstance("pi-2", "jane.employee");
        mockProcessDefinition("def-1", "Leave Request");
        when(emailResolver.resolveEmail("nobody")).thenReturn(Optional.empty());

        FlowableEntityEventImpl event = new FlowableEntityEventImpl(task, FlowableEngineEventType.TASK_ASSIGNED);
        listener.onEvent(event);

        verifyNoInteractions(notificationService);
    }

    @Test
    void onTaskCompleted_sendsEmailToInitiator_whenAssigneeDiffersFromInitiator() {
        TaskEntityImpl task = new TaskEntityImpl();
        task.setId("task-3");
        task.setName("Manager Approval");
        task.setAssignee("john.manager");
        task.setProcessInstanceId("pi-3");
        task.setProcessDefinitionId("def-1");

        mockProcessInstance("pi-3", "jane.employee");
        mockProcessDefinition("def-1", "Leave Request");
        when(emailResolver.resolveEmail("jane.employee")).thenReturn(Optional.of("jane.employee@werkflow.local"));

        FlowableEntityEventImpl event = new FlowableEntityEventImpl(task, FlowableEngineEventType.TASK_COMPLETED);
        listener.onEvent(event);

        verify(notificationService).sendTaskCompletedNotification(
            eq("task-3"),
            eq(List.of("jane.employee@werkflow.local")),
            eq("completed"),
            eq(""),
            eq("Manager Approval"),
            eq("Leave Request"),
            eq("john.manager"),
            eq("pi-3")
        );
    }

    @Test
    void onTaskCompleted_skipsNotification_whenAssigneeIsInitiator() {
        TaskEntityImpl task = new TaskEntityImpl();
        task.setId("task-4");
        task.setName("Submit Request");
        task.setAssignee("jane.employee");
        task.setProcessInstanceId("pi-4");
        task.setProcessDefinitionId("def-1");

        mockProcessInstance("pi-4", "jane.employee");

        FlowableEntityEventImpl event = new FlowableEntityEventImpl(task, FlowableEngineEventType.TASK_COMPLETED);
        listener.onEvent(event);

        verifyNoInteractions(notificationService);
    }

    @Test
    void isFailOnException_returnsFalse() {
        assertThat(listener.isFailOnException()).isFalse();
    }

    private void mockProcessInstance(String processInstanceId, String startUserId) {
        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getStartUserId()).thenReturn(startUserId);
        when(runtimeService.createProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult()).thenReturn(pi);
    }

    private void mockProcessDefinition(String definitionId, String name) {
        ProcessDefinition def = mock(ProcessDefinition.class);
        when(def.getName()).thenReturn(name);
        when(repositoryService.getProcessDefinition(definitionId)).thenReturn(def);
    }
}
```

- [ ] **Step 7: Run test to verify it fails**

```bash
cd services/engine
./mvnw test -pl . -Dtest=GlobalTaskNotificationListenerTest -q 2>&1 | tail -10
```

Expected: FAIL — `GlobalTaskNotificationListener` class not found.

---

## Task 4: GlobalTaskNotificationListener — implementation

**Files:**
- Create: `services/engine/src/main/java/com/werkflow/engine/listener/GlobalTaskNotificationListener.java`

- [ ] **Step 8: Implement GlobalTaskNotificationListener**

```java
package com.werkflow.engine.listener;

import com.werkflow.engine.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.event.FlowableEngineEntityEvent;
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
                log.info("GlobalTaskNotificationListener: TASK_ASSIGNED — notifying assignee='{}' task='{}'",
                    assignee, taskName);
                notificationService.sendTaskAssignedNotification(task.getId(), email, taskName, processName);
            },
            () -> log.debug("GlobalTaskNotificationListener: TASK_ASSIGNED — no email for assignee='{}'", assignee)
        );
    }

    private void handleTaskCompleted(TaskEntity task) {
        String assignee       = task.getAssignee();
        String processInstId  = task.getProcessInstanceId();
        String startUserId    = resolveStartUserId(processInstId);

        // Skip notification when the person completing the task is also the process initiator
        // (covers "Submit Request" user tasks where requester fills their own form)
        if (startUserId == null || Objects.equals(assignee, startUserId)) {
            log.debug("GlobalTaskNotificationListener: TASK_COMPLETED — skipping self-completion task='{}'",
                task.getId());
            return;
        }

        String taskName    = taskName(task);
        String processName = processName(task.getProcessDefinitionId());
        String completedBy = assignee != null ? assignee : "System";

        emailResolver.resolveEmail(startUserId).ifPresentOrElse(
            email -> {
                log.info("GlobalTaskNotificationListener: TASK_COMPLETED — notifying initiator='{}' task='{}'",
                    startUserId, taskName);
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
            () -> log.debug("GlobalTaskNotificationListener: TASK_COMPLETED — no email for initiator='{}'", startUserId)
        );
    }

    private String resolveStartUserId(String processInstanceId) {
        try {
            ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
            return pi != null ? pi.getStartUserId() : null;
        } catch (Exception e) {
            log.warn("GlobalTaskNotificationListener: could not resolve startUserId for pi={}: {}",
                processInstanceId, e.getMessage());
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
```

- [ ] **Step 9: Run tests to verify they pass**

```bash
cd services/engine
./mvnw test -pl . -Dtest="UserEmailResolverTest,GlobalTaskNotificationListenerTest" -q 2>&1 | tail -10
```

Expected: `Tests run: 9, Failures: 0, Errors: 0`

- [ ] **Step 10: Commit**

```bash
cd services/engine
git add src/main/java/com/werkflow/engine/listener/GlobalTaskNotificationListener.java \
        src/test/java/com/werkflow/engine/listener/GlobalTaskNotificationListenerTest.java
git commit -m "feat(engine): add GlobalTaskNotificationListener — auto email on task assign/complete"
```

---

## Task 5: Register listener in FlowableConfig

**Files:**
- Modify: `services/engine/src/main/java/com/werkflow/engine/config/FlowableConfig.java`

- [ ] **Step 11: Update FlowableConfig to register the listener**

Replace the existing `processEngineConfigurer()` bean in `FlowableConfig.java` with:

```java
package com.werkflow.engine.config;

import com.werkflow.engine.listener.GlobalTaskNotificationListener;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class FlowableConfig {

    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> processEngineConfigurer(
        GlobalTaskNotificationListener globalTaskNotificationListener
    ) {
        return engineConfiguration -> {
            engineConfiguration.setCreateDiagramOnDeploy(false);
            engineConfiguration.setEnableSafeBpmnXml(true);
            engineConfiguration.setActivityFontName("Arial");
            engineConfiguration.setLabelFontName("Arial");
            engineConfiguration.setAnnotationFontName("Arial");

            // Register global task notification listener for automatic email dispatch
            // This eliminates the need for explicit NOTIFICATION service task nodes in BPMN
            Map<String, List<FlowableEventListener>> typedListeners = Map.of(
                FlowableEngineEventType.TASK_ASSIGNED.name(), List.of(globalTaskNotificationListener),
                FlowableEngineEventType.TASK_COMPLETED.name(), List.of(globalTaskNotificationListener)
            );
            engineConfiguration.setTypedEventListeners(typedListeners);
        };
    }
}
```

- [ ] **Step 12: Build the engine module**

```bash
cd services/engine
./mvnw compile -q 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`

- [ ] **Step 13: Run the full engine test suite**

```bash
cd services/engine
./mvnw test -q 2>&1 | tail -20
```

Expected: No failures in existing tests. New listener tests pass.

- [ ] **Step 14: Commit**

```bash
cd services/engine
git add src/main/java/com/werkflow/engine/config/FlowableConfig.java
git commit -m "feat(engine): register GlobalTaskNotificationListener in FlowableConfig"
```

---

## Task 6: Smoke test with running stack

- [ ] **Step 15: Start the stack**

```bash
cd infrastructure/docker
docker compose up -d --wait
```

Expected: All services healthy (portal:4000, engine:8081, keycloak:8090, mailpit:8025).

- [ ] **Step 16: Start a process and claim a task**

Log in as `jane.employee` (TempPassword123!) → navigate to `/processes` → start "General Approval" → submit.

Then log in as `john.manager` (TempPassword123!) → navigate to `/tasks` → claim the approval task.

- [ ] **Step 17: Verify task-assigned email in Mailpit**

```bash
curl -s http://localhost:8025/api/v1/messages | jq '.messages[0] | {to: .To, subject: .Subject}'
```

Expected: email to `john.manager@werkflow.local` (or the email set in Keycloak for john.manager), subject containing the task name.

- [ ] **Step 18: Approve the task**

As `john.manager` → open task → click Approve → submit.

- [ ] **Step 19: Verify task-completed email in Mailpit**

```bash
curl -s http://localhost:8025/api/v1/messages | jq '[.messages[] | {to: .To, subject: .Subject}]'
```

Expected: second email to `jane.employee@werkflow.local` with subject indicating task completion.

- [ ] **Step 20: Final commit (if any fixes applied during smoke test)**

```bash
cd services/engine
git add -p
git commit -m "fix(engine): smoke test corrections for GlobalTaskNotificationListener"
```

---

## Definition of Done

- [ ] `UserEmailResolverTest` — 4 tests pass
- [ ] `GlobalTaskNotificationListenerTest` — 5 tests pass
- [ ] `FlowableConfig` registers the listener for `TASK_ASSIGNED` and `TASK_COMPLETED`
- [ ] Smoke test confirms emails arrive in Mailpit on task assign and task complete
- [ ] No regression in existing engine test suite
- [ ] BPMN workflow journey specs (Layer 3 E2E) will NOT include NOTIFICATION service task nodes — emails fire automatically

---

## Notes for E2E Test Authors

After S28.5 is complete:
- Remove all `→ Email Task "..." [NOTIFICATION]` nodes from the 4 workflow BPMN structures in the spec
- Mailpit assertions in Layer 3 tests assert on the auto-fired "task completed" email sent to the process initiator
- The "task assigned" email to the approver is also assertable (sent when manager claims the task)
