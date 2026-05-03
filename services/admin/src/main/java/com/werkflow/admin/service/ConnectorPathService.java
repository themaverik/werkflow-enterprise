package com.werkflow.admin.service;

import com.werkflow.admin.dto.connector.ConnectorPathRequest;
import com.werkflow.admin.dto.connector.ConnectorPathResponse;
import com.werkflow.admin.entity.TenantConnectorPath;
import com.werkflow.admin.repository.TenantConnectorPathRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ConnectorPathService {

    private final TenantConnectorPathRepository pathRepo;

    @Transactional(readOnly = true)
    public List<ConnectorPathResponse> list(String connectorKey, String tenantCode) {
        return pathRepo.findByConnectorKeyAndTenantCode(connectorKey, tenantCode)
            .stream().map(this::toResponse).toList();
    }

    @Transactional
    public ConnectorPathResponse upsert(String connectorKey, String tenantCode, ConnectorPathRequest request) {
        TenantConnectorPath path = pathRepo
            .findByConnectorKeyAndTenantCodeAndPathAndHttpMethod(
                connectorKey, tenantCode, request.getPath(), request.getHttpMethod())
            .orElseGet(TenantConnectorPath::new);

        path.setConnectorKey(connectorKey);
        path.setTenantCode(tenantCode);
        path.setPath(request.getPath());
        path.setHttpMethod(request.getHttpMethod());
        path.setInteractionType(request.getInteractionType());
        path.setDescription(request.getDescription());
        path.setRequestSchema(request.getRequestSchema());
        path.setResponseSchema(request.getResponseSchema());
        path.setVariableMappings(request.getVariableMappings());

        return toResponse(pathRepo.save(path));
    }

    @Transactional
    public void delete(Long id, String connectorKey, String tenantCode) {
        TenantConnectorPath path = pathRepo.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Connector path not found: " + id));
        if (!path.getConnectorKey().equals(connectorKey) || !path.getTenantCode().equals(tenantCode)) {
            throw new IllegalArgumentException("Path does not belong to this connector/tenant");
        }
        pathRepo.delete(path);
    }

    private ConnectorPathResponse toResponse(TenantConnectorPath p) {
        ConnectorPathResponse r = new ConnectorPathResponse();
        r.setId(p.getId());
        r.setConnectorKey(p.getConnectorKey());
        r.setTenantCode(p.getTenantCode());
        r.setPath(p.getPath());
        r.setHttpMethod(p.getHttpMethod());
        r.setInteractionType(p.getInteractionType());
        r.setDescription(p.getDescription());
        r.setRequestSchema(p.getRequestSchema());
        r.setResponseSchema(p.getResponseSchema());
        r.setVariableMappings(p.getVariableMappings());
        r.setCreatedAt(p.getCreatedAt());
        r.setUpdatedAt(p.getUpdatedAt());
        return r;
    }
}
