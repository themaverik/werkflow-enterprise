package com.werkflow.admin.service;

import com.werkflow.admin.dto.RoleGroupMappingRequest;
import com.werkflow.admin.dto.RoleGroupMappingResponse;
import com.werkflow.admin.entity.RoleGroupMapping;
import com.werkflow.admin.repository.RoleGroupMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleGroupMappingService {

    private final RoleGroupMappingRepository repository;

    @Transactional(readOnly = true)
    public List<RoleGroupMappingResponse> listByTenant(String tenantCode) {
        return repository.findByTenantCodeOrderByRoleName(tenantCode)
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    /**
     * Returns role → [groups] map for the tenant, used by the engine's AdminServiceClient.
     */
    @Transactional(readOnly = true)
    public Map<String, List<String>> getGroupsByRole(String tenantCode) {
        return repository.findByTenantCodeOrderByRoleName(tenantCode)
            .stream()
            .collect(Collectors.groupingBy(
                RoleGroupMapping::getRoleName,
                Collectors.mapping(RoleGroupMapping::getGroupName, Collectors.toList())
            ));
    }

    @Transactional
    public RoleGroupMappingResponse create(RoleGroupMappingRequest request) {
        if (repository.existsByTenantCodeAndRoleNameAndGroupName(
                request.tenantCode(), request.roleName(), request.groupName())) {
            return toResponse(repository.findByTenantCodeOrderByRoleName(request.tenantCode())
                .stream()
                .filter(m -> m.getRoleName().equals(request.roleName())
                    && m.getGroupName().equals(request.groupName()))
                .findFirst()
                .orElseThrow());
        }
        RoleGroupMapping mapping = new RoleGroupMapping();
        mapping.setTenantCode(request.tenantCode());
        mapping.setRoleName(request.roleName());
        mapping.setGroupName(request.groupName());
        return toResponse(repository.save(mapping));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new RuntimeException("Role group mapping not found: " + id);
        }
        repository.deleteById(id);
    }

    private RoleGroupMappingResponse toResponse(RoleGroupMapping mapping) {
        return new RoleGroupMappingResponse(
            mapping.getId(),
            mapping.getTenantCode(),
            mapping.getRoleName(),
            mapping.getGroupName(),
            mapping.getCreatedAt()
        );
    }
}
