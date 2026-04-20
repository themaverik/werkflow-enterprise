package com.werkflow.engine.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final ProcessAuditLogRepository repository;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'AUDIT:VIEW')")
    public List<ProcessAuditLog> getAuditLogs(
            @RequestParam(required = false) String processInstanceId,
            @RequestParam(required = false) String processDefinitionKey) {

        if (processInstanceId == null && processDefinitionKey == null) {
            throw new IllegalArgumentException(
                "At least one filter required: processInstanceId or processDefinitionKey");
        }
        if (processInstanceId != null) {
            return repository.findByProcessInstanceId(processInstanceId);
        }
        return repository.findByProcessDefinitionKey(processDefinitionKey);
    }
}
