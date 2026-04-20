package com.werkflow.engine.security.guard;

import com.werkflow.engine.dto.JwtUserContext;
import com.werkflow.engine.util.JwtClaimsExtractor;
import com.werkflow.engine.workflow.FlowableGroupResolver;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TaskGuard implements DomainGuard {

    @Override
    public String supports() {
        return "Task";
    }


    private final JwtClaimsExtractor jwtClaimsExtractor;
    private final FlowableGroupResolver groupResolver;
    private final TaskService flowableTaskService;
    private final RuntimeService runtimeService;

    public TaskGuard(JwtClaimsExtractor jwtClaimsExtractor,
                     FlowableGroupResolver groupResolver,
                     TaskService flowableTaskService,
                     RuntimeService runtimeService) {
        this.jwtClaimsExtractor = jwtClaimsExtractor;
        this.groupResolver = groupResolver;
        this.flowableTaskService = flowableTaskService;
        this.runtimeService = runtimeService;
    }

    public boolean canAct(Authentication auth, Serializable taskId, String action) {
        return switch (action) {
            case "CLAIM"    -> checkCandidateGroup(auth, taskId.toString());
            case "COMPLETE" -> checkAssignee(auth, taskId.toString());
            default         -> false;
        };
    }

    private boolean checkCandidateGroup(Authentication auth, String taskId) {
        Task task = flowableTaskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null || task.getAssignee() != null) {
            return false;
        }

        Jwt jwt = (Jwt) auth.getPrincipal();
        JwtUserContext userContext = jwtClaimsExtractor.extractUserContext(jwt);
        String userId = userContext.getUserId();

        // Initiator cannot claim their own request
        Object initiatorId = runtimeService.getVariable(task.getProcessInstanceId(), "initiatorId");
        if (userId != null && userId.equals(initiatorId)) {
            return false;
        }

        List<String> userGroups = groupResolver.resolveGroups(userContext);

        Set<String> candidateGroups = flowableTaskService.getIdentityLinksForTask(taskId)
            .stream()
            .filter(l -> "candidate".equals(l.getType()) && l.getGroupId() != null)
            .map(IdentityLink::getGroupId)
            .collect(Collectors.toSet());

        return userGroups.stream().anyMatch(candidateGroups::contains);
    }

    private boolean checkAssignee(Authentication auth, String taskId) {
        Jwt jwt = (Jwt) auth.getPrincipal();
        String currentUserId = jwtClaimsExtractor.extractUserContext(jwt).getUserId();
        Task task = flowableTaskService.createTaskQuery().taskId(taskId).singleResult();
        return task != null && currentUserId != null && currentUserId.equals(task.getAssignee());
    }
}
