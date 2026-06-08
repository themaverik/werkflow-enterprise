package com.werkflow.engine.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProcessAuditLogRepository extends JpaRepository<ProcessAuditLog, Long> {

    List<ProcessAuditLog> findByProcessInstanceId(String processInstanceId);

    List<ProcessAuditLog> findByProcessDefinitionKey(String processDefinitionKey);

    List<ProcessAuditLog> findByProcessInstanceIdAndTenantId(String processInstanceId, String tenantId);

    List<ProcessAuditLog> findByProcessDefinitionKeyAndTenantId(String processDefinitionKey, String tenantId);
}
