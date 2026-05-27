package com.werkflow.engine.controller;

import com.werkflow.engine.dto.JwtUserContext;
import com.werkflow.engine.dto.TaskResponse;
import com.werkflow.engine.service.TaskService;
import com.werkflow.engine.util.JwtClaimsExtractor;
import com.werkflow.engine.workflow.FlowableGroupResolver;
import org.flowable.engine.HistoryService;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.identitylink.api.IdentityLinkType;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskControllerAuthTest {

    @Mock private TaskService taskService;
    @Mock private HistoryService historyService;
    @Mock private JwtClaimsExtractor jwtClaimsExtractor;
    @Mock private FlowableGroupResolver groupResolver;
    @Mock private org.flowable.engine.TaskService flowableTaskService;
    @Mock private Jwt jwt;
    @Mock private Task task;
    @Mock private TaskQuery taskQuery;
    @Mock private IdentityLink candidateLink;

    @InjectMocks
    private TaskController controller;

    @BeforeEach
    void setUp() {
        JwtUserContext ctx = JwtUserContext.builder().userId("user-1").build();
        // Production code uses jwt.getSubject() (L-4: stable Keycloak UUID), not preferred_username
        when(jwt.getSubject()).thenReturn("user-1");
        when(jwtClaimsExtractor.extractUserContext(jwt)).thenReturn(ctx);
        when(groupResolver.resolveGroups(ctx)).thenReturn(List.of("DOA_L1"));
        when(flowableTaskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(task.getAssignee()).thenReturn("user-1");
        when(flowableTaskService.getIdentityLinksForTask("task-1")).thenReturn(List.of(candidateLink));
        when(candidateLink.getType()).thenReturn(IdentityLinkType.CANDIDATE);
        when(candidateLink.getGroupId()).thenReturn("DOA_L1");
    }

    @Test
    void unclaim_unauthorizedUser_throws403() {
        JwtUserContext otherCtx = JwtUserContext.builder().userId("user-2").build();
        Jwt otherJwt = mock(Jwt.class);
        when(otherJwt.getSubject()).thenReturn("user-2");
        when(jwtClaimsExtractor.extractUserContext(otherJwt)).thenReturn(otherCtx);
        when(groupResolver.resolveGroups(otherCtx)).thenReturn(List.of("EMPLOYEE"));

        assertThatThrownBy(() -> controller.unclaimTask("task-1", otherJwt))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unclaim_assignedUser_succeeds() {
        controller.unclaimTask("task-1", jwt);
        verify(taskService).unclaimTask("task-1");
    }

    @Test
    void getTaskHistory_unauthorizedUser_throws403() {
        JwtUserContext otherCtx = JwtUserContext.builder().userId("user-2").build();
        Jwt otherJwt = mock(Jwt.class);
        when(otherJwt.getSubject()).thenReturn("user-2");
        when(jwtClaimsExtractor.extractUserContext(otherJwt)).thenReturn(otherCtx);
        when(groupResolver.resolveGroups(otherCtx)).thenReturn(List.of("EMPLOYEE"));
        when(taskService.findActiveTask("task-1")).thenReturn(task);

        assertThatThrownBy(() -> controller.getTaskHistory("task-1", otherJwt))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
