package com.werkflow.engine.delegate;

import com.werkflow.engine.client.AdminServiceClient;
import com.werkflow.engine.client.UserProfileDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * Sets the owningDepartment process variable from the submitter's ERP department profile.
 * Falls back to any form-supplied owningDepartment value when ERP is unavailable.
 * ADR-005: department is ERP-owned; engine resolves it at process start via this delegate.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SetOwningDepartmentDelegate implements JavaDelegate {

    private final AdminServiceClient adminServiceClient;

    @Override
    public void execute(DelegateExecution execution) {
        String submitterId = resolveSubmitterId(execution);
        String tenantCode  = execution.getTenantId() != null ? execution.getTenantId() : "default";

        if (submitterId == null || submitterId.isBlank()) {
            log.warn("SetOwningDepartmentDelegate: no submitterId in process {}", execution.getProcessInstanceId());
            return;
        }

        try {
            UserProfileDto profile = adminServiceClient.getUserProfile(submitterId, tenantCode);
            if (profile != null && profile.getDepartmentCode() != null && !profile.getDepartmentCode().isBlank()) {
                execution.setVariable("owningDepartment", profile.getDepartmentCode());
                log.debug("SetOwningDepartmentDelegate: set owningDepartment={} for process {}",
                        profile.getDepartmentCode(), execution.getProcessInstanceId());
                return;
            }
        } catch (Exception e) {
            log.warn("SetOwningDepartmentDelegate: ERP lookup failed for user {} — {}; using form fallback",
                    submitterId, e.getMessage());
        }

        // Preserve any form-supplied value already in the process context
        Object existing = execution.getVariable("owningDepartment");
        if (existing != null) {
            log.debug("SetOwningDepartmentDelegate: retained form-supplied owningDepartment={} for process {}",
                    existing, execution.getProcessInstanceId());
        } else {
            log.debug("SetOwningDepartmentDelegate: no department resolved for process {}",
                    execution.getProcessInstanceId());
        }
    }

    private String resolveSubmitterId(DelegateExecution execution) {
        Object submitterId = execution.getVariable("submitterId");
        if (submitterId != null) return submitterId.toString();
        Object initiator = execution.getVariable("initiator");
        if (initiator != null) return initiator.toString();
        return null;
    }
}
