package com.werkflow.engine.audit;

import com.werkflow.engine.util.JwtClaimsExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final ProcessAuditLogRepository repository;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'AUDIT:VIEW')")
    public List<ProcessAuditLog> getAuditLogs(
            @RequestParam(required = false) String processInstanceId,
            @RequestParam(required = false) String processDefinitionKey,
            @AuthenticationPrincipal Jwt jwt) {

        if (processInstanceId == null && processDefinitionKey == null) {
            throw new IllegalArgumentException(
                "At least one filter required: processInstanceId or processDefinitionKey");
        }

        boolean isSuperAdmin = jwtClaimsExtractor.hasRole(jwt, "SUPER_ADMIN");
        if (isSuperAdmin) {
            if (processInstanceId != null) {
                return repository.findByProcessInstanceId(processInstanceId);
            }
            return repository.findByProcessDefinitionKey(processDefinitionKey);
        }

        String tenantId = jwtClaimsExtractor.getTenantCode(jwt);
        if (processInstanceId != null) {
            return repository.findByProcessInstanceIdAndTenantId(processInstanceId, tenantId);
        }
        return repository.findByProcessDefinitionKeyAndTenantId(processDefinitionKey, tenantId);
    }
}
