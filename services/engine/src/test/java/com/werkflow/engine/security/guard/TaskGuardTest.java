package com.werkflow.engine.security.guard;

import com.werkflow.engine.dto.JwtUserContext;
import com.werkflow.engine.util.JwtClaimsExtractor;
import com.werkflow.engine.workflow.FlowableGroupResolver;
import org.flowable.engine.RuntimeService;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskGuardTest {

    @Mock private JwtClaimsExtractor jwtClaimsExtractor;
    @Mock private FlowableGroupResolver groupResolver;
    @Mock private org.flowable.engine.TaskService flowableTaskService;
    @Mock private RuntimeService runtimeService;
    @Mock private Authentication auth;
    @Mock private Jwt jwt;
    @Mock private Task task;
    @Mock private TaskQuery taskQuery;
    @Mock private IdentityLink candidateLink;

    @InjectMocks
    private TaskGuard guard;

    private JwtUserContext userContext;

    @BeforeEach
    void setUp() {
        when(auth.getPrincipal()).thenReturn(jwt);
        userContext = JwtUserContext.builder().userId("user-1").build();
        when(jwtClaimsExtractor.extractUserContext(jwt)).thenReturn(userContext);
        when(flowableTaskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(task.getProcessInstanceId()).thenReturn("proc-1");
        when(runtimeService.getVariable("proc-1", "initiatorId")).thenReturn("other-user");
    }

    @Test
    void claim_userGroupMatchesCandidateGroup_returnsTrue() {
        when(groupResolver.resolveGroups(userContext)).thenReturn(List.of("EMPLOYEE", "DOA_L1"));
        when(task.getAssignee()).thenReturn(null);
        when(flowableTaskService.getIdentityLinksForTask("task-1")).thenReturn(List.of(candidateLink));
        when(candidateLink.getType()).thenReturn("candidate");
        when(candidateLink.getGroupId()).thenReturn("DOA_L1");

        assertThat(guard.canAct(auth, "task-1", "CLAIM")).isTrue();
    }

    @Test
    void claim_noCandidateGroupMatch_returnsFalse() {
        when(groupResolver.resolveGroups(userContext)).thenReturn(List.of("EMPLOYEE"));
        when(task.getAssignee()).thenReturn(null);
        when(flowableTaskService.getIdentityLinksForTask("task-1")).thenReturn(List.of(candidateLink));
        when(candidateLink.getType()).thenReturn("candidate");
        when(candidateLink.getGroupId()).thenReturn("DOA_L3");

        assertThat(guard.canAct(auth, "task-1", "CLAIM")).isFalse();
    }

    @Test
    void claim_taskAlreadyAssigned_returnsFalse() {
        when(task.getAssignee()).thenReturn("other-user");

        assertThat(guard.canAct(auth, "task-1", "CLAIM")).isFalse();
        verify(flowableTaskService, never()).getIdentityLinksForTask(any());
    }

    @Test
    void claim_selfClaimByInitiator_returnsFalse() {
        // Initiator (user-1) cannot claim their own process task
        when(runtimeService.getVariable("proc-1", "initiatorId")).thenReturn("user-1");
        when(task.getAssignee()).thenReturn(null);
        when(groupResolver.resolveGroups(userContext)).thenReturn(List.of("EMPLOYEE", "DOA_L1"));
        when(flowableTaskService.getIdentityLinksForTask("task-1")).thenReturn(List.of(candidateLink));
        when(candidateLink.getType()).thenReturn("candidate");
        when(candidateLink.getGroupId()).thenReturn("DOA_L1");

        assertThat(guard.canAct(auth, "task-1", "CLAIM")).isFalse();
    }

    @Test
    void complete_userIsAssignee_returnsTrue() {
        when(task.getAssignee()).thenReturn("user-1");

        assertThat(guard.canAct(auth, "task-1", "COMPLETE")).isTrue();
    }

    @Test
    void complete_differentUserAssigned_returnsFalse() {
        when(task.getAssignee()).thenReturn("other-user");

        assertThat(guard.canAct(auth, "task-1", "COMPLETE")).isFalse();
    }

    @Test
    void unknownAction_returnsFalse() {
        // Unknown actions are denied — guard is default-deny
        assertThat(guard.canAct(auth, "task-1", "VIEW")).isFalse();
    }
}
