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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
     * Keys that must never be exposed in API responses or supplied by users at process start.
     * CRIT-01 / HIGH-05: prevents JWT token leakage and security variable override.
     */
    private static final Set<String> SECURITY_VAR_DENYLIST = new HashSet<>(Arrays.asList(
        "authorizationToken", "owningDepartment", "submitterId", "initiator"
    ));

    /**
     * Strips security-sensitive variable keys from a variables map (case-insensitive prefix check
     * for token/jwt/bearer keys, and exact match for denylist keys).
     */
    static Map<String, Object> stripSecurityVariables(Map<String, Object> vars) {
        if (vars == null) return Map.of();
        Map<String, Object> result = new HashMap<>(vars);
        result.keySet().removeIf(key -> {
            if (key == null) return true;
            if (SECURITY_VAR_DENYLIST.contains(key)) return true;
            // Strip keys containing EL metacharacters
            if (key.contains("$") || key.contains("#{")) return true;
            // Strip token-related keys (case-insensitive prefix match)
            String lower = key.toLowerCase();
            return lower.equals("token") || lower.equals("jwt") || lower.equals("bearer")
                || lower.startsWith("authorizationtoken") || lower.startsWith("token")
                || lower.startsWith("jwt") || lower.startsWith("bearer");
        });
        return result;
    }

    /**
     * Start a new process instance
     */
    @Transactional
    public ProcessInstanceResponse startProcessInstance(StartProcessRequest request, String userId, String tenantId) {
        log.info("Starting process instance: {} by user: {}", request.getProcessDefinitionKey(), userId);

        // CRIT-01 / HIGH-05: strip user-supplied security variables before passing to Flowable
        Map<String, Object> variables = stripSecurityVariables(
            request.getVariables() != null ? request.getVariables() : Map.of()
        );

        // Normalize: map estimatedAmount to requestAmount for BPMN gateway expressions
        if (variables.containsKey("estimatedAmount") && !variables.containsKey("requestAmount")) {
            variables = new HashMap<>(variables);
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
     * Get all active process instances scoped to the caller's tenant.
     * CRIT-02: tenant isolation enforced via processInstanceTenantId.
     */
    public List<ProcessInstanceResponse> getAllProcessInstances(String tenantId) {
        log.debug("Fetching all active process instances for tenant: {}", tenantId);

        List<ProcessInstance> instances = runtimeService.createProcessInstanceQuery()
            .processInstanceTenantId(tenantId)
            .active()
            .list();

        return instances.stream()
            .map(pi -> mapToResponse(pi, null))
            .collect(Collectors.toList());
    }

    /**
     * Get process instance by ID.
     * CRIT-03: tenant ownership check — the instance's tenantId must match the caller's.
     */
    public ProcessInstanceResponse getProcessInstanceById(String id, String callerTenantId) {
        log.debug("Fetching process instance by ID: {}", id);

        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
            .processInstanceId(id)
            .singleResult();

        if (processInstance == null) {
            throw new RuntimeException("Process instance not found with ID: " + id);
        }

        // Tenant ownership check — SUPER_ADMIN callers pass null to skip
        if (callerTenantId != null && !callerTenantId.isBlank()
                && !callerTenantId.equals(processInstance.getTenantId())) {
            log.warn("Tenant mismatch: caller tenant={} vs instance tenant={} for process {}",
                callerTenantId, processInstance.getTenantId(), id);
            throw new RuntimeException("Process instance not found with ID: " + id);
        }

        Map<String, Object> variables = stripSecurityVariables(runtimeService.getVariables(id));

        return mapToResponse(processInstance, variables);
    }

    /**
     * Get process instances by process definition key scoped to the caller's tenant.
     * CRIT-02: tenant isolation enforced via processInstanceTenantId.
     */
    public List<ProcessInstanceResponse> getProcessInstancesByDefinitionKey(String key, String tenantId) {
        log.debug("Fetching process instances for process definition: {} tenant: {}", key, tenantId);

        List<ProcessInstance> instances = runtimeService.createProcessInstanceQuery()
            .processDefinitionKey(key)
            .processInstanceTenantId(tenantId)
            .active()
            .list();

        return instances.stream()
            .map(pi -> {
                Map<String, Object> variables = stripSecurityVariables(runtimeService.getVariables(pi.getId()));
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
     * Get process variables, stripping security-sensitive keys before returning.
     * CRIT-01 / CRIT-03: prevents token/secret leakage via the variables API.
     */
    public Map<String, Object> getProcessVariables(String processInstanceId, String callerTenantId) {
        log.debug("Fetching variables for process instance: {}", processInstanceId);

        // Tenant check before exposing variables
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();

        if (processInstance == null) {
            throw new RuntimeException("Process instance not found with ID: " + processInstanceId);
        }

        if (callerTenantId != null && !callerTenantId.isBlank()
                && !callerTenantId.equals(processInstance.getTenantId())) {
            throw new RuntimeException("Process instance not found with ID: " + processInstanceId);
        }

        return stripSecurityVariables(runtimeService.getVariables(processInstanceId));
    }

    /**
     * Set process variables, stripping security-sensitive keys to prevent override.
     * HIGH-05: prevents EL injection and security variable override.
     */
    @Transactional
    public void setProcessVariables(String processInstanceId, Map<String, Object> variables) {
        log.info("Setting variables for process instance: {}", processInstanceId);

        Map<String, Object> safe = stripSecurityVariables(variables);
        runtimeService.setVariables(processInstanceId, safe);

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
