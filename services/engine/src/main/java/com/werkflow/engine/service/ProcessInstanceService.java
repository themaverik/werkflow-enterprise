package com.werkflow.engine.service;

import com.werkflow.engine.dto.ProcessInstanceResponse;
import com.werkflow.engine.dto.StartProcessRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.IdentityService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for managing process instances
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessInstanceService {

    private final RuntimeService runtimeService;
    private final IdentityService identityService;

    /**
     * Start a new process instance
     */
    @Transactional
    public ProcessInstanceResponse startProcessInstance(StartProcessRequest request, String userId, String jwtToken) {
        log.info("Starting process instance: {} by user: {}", request.getProcessDefinitionKey(), userId);

        Map<String, Object> variables = request.getVariables() != null
            ? new HashMap<>(request.getVariables()) : new HashMap<>();

        if (jwtToken != null) {
            variables.put("authorizationToken", "Bearer " + jwtToken);
        }

        // Normalize: map estimatedAmount to requestAmount for BPMN gateway expressions
        if (variables.containsKey("estimatedAmount") && !variables.containsKey("requestAmount")) {
            variables.put("requestAmount", variables.get("estimatedAmount"));
        }

        // Set authenticated user so Flowable records startUserId on the process instance
        try {
            identityService.setAuthenticatedUserId(userId);

            ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                request.getProcessDefinitionKey(),
                request.getBusinessKey(),
                variables
            );

            log.info("Process instance started successfully. ID: {}", processInstance.getId());

            return mapToResponse(processInstance, variables);
        } finally {
            identityService.setAuthenticatedUserId(null);
        }
    }

    /**
     * Get all active process instances
     */
    public List<ProcessInstanceResponse> getAllProcessInstances() {
        log.debug("Fetching all active process instances");

        List<ProcessInstance> instances = runtimeService.createProcessInstanceQuery()
            .active()
            .list();

        return instances.stream()
            .map(pi -> mapToResponse(pi, null))
            .collect(Collectors.toList());
    }

    /**
     * Get process instance by ID
     */
    public ProcessInstanceResponse getProcessInstanceById(String id) {
        log.debug("Fetching process instance by ID: {}", id);

        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
            .processInstanceId(id)
            .singleResult();

        if (processInstance == null) {
            throw new RuntimeException("Process instance not found with ID: " + id);
        }

        Map<String, Object> variables = runtimeService.getVariables(id);

        return mapToResponse(processInstance, variables);
    }

    /**
     * Get process instances by process definition key
     */
    public List<ProcessInstanceResponse> getProcessInstancesByDefinitionKey(String key) {
        log.debug("Fetching process instances for process definition: {}", key);

        List<ProcessInstance> instances = runtimeService.createProcessInstanceQuery()
            .processDefinitionKey(key)
            .active()
            .list();

        return instances.stream()
            .map(pi -> {
                Map<String, Object> variables = runtimeService.getVariables(pi.getId());
                return mapToResponse(pi, variables);
            })
            .collect(Collectors.toList());
    }

    /**
     * Delete (terminate) a process instance
     */
    @Transactional
    public void deleteProcessInstance(String processInstanceId, String reason) {
        log.info("Deleting process instance: {} with reason: {}", processInstanceId, reason);

        runtimeService.deleteProcessInstance(processInstanceId, reason);

        log.info("Process instance deleted successfully");
    }

    /**
     * Suspend a process instance
     */
    @Transactional
    public void suspendProcessInstance(String processInstanceId) {
        log.info("Suspending process instance: {}", processInstanceId);

        runtimeService.suspendProcessInstanceById(processInstanceId);

        log.info("Process instance suspended successfully");
    }

    /**
     * Activate a process instance
     */
    @Transactional
    public void activateProcessInstance(String processInstanceId) {
        log.info("Activating process instance: {}", processInstanceId);

        runtimeService.activateProcessInstanceById(processInstanceId);

        log.info("Process instance activated successfully");
    }

    /**
     * Get process variables
     */
    public Map<String, Object> getProcessVariables(String processInstanceId) {
        log.debug("Fetching variables for process instance: {}", processInstanceId);

        return runtimeService.getVariables(processInstanceId);
    }

    /**
     * Set process variables
     */
    @Transactional
    public void setProcessVariables(String processInstanceId, Map<String, Object> variables) {
        log.info("Setting variables for process instance: {}", processInstanceId);

        runtimeService.setVariables(processInstanceId, variables);

        log.info("Variables set successfully");
    }

    /**
     * Map ProcessInstance entity to response DTO
     */
    private ProcessInstanceResponse mapToResponse(ProcessInstance pi, Map<String, Object> variables) {
        return ProcessInstanceResponse.builder()
            .id(pi.getId())
            .processDefinitionId(pi.getProcessDefinitionId())
            .processDefinitionKey(pi.getProcessDefinitionKey())
            .processDefinitionName(pi.getProcessDefinitionName())
            .processDefinitionVersion(pi.getProcessDefinitionVersion())
            .businessKey(pi.getBusinessKey())
            .startTime(pi.getStartTime() != null ?
                pi.getStartTime().toInstant().atZone(ZoneId.systemDefault()).toInstant() : null)
            .startUserId(pi.getStartUserId())
            .suspended(pi.isSuspended())
            .ended(pi.isEnded())
            .variables(variables)
            .tenantId(pi.getTenantId())
            .build();
    }
}
