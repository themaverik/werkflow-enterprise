package com.werkflow.admin.service;

import com.werkflow.admin.repository.TenantRolePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenantRolePermissionService {

    private final TenantRolePermissionRepository repository;

    @Transactional(readOnly = true)
    public Set<String> getPermissionsForRoles(String tenantCode, List<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) return Set.of();
        return repository.findByTenantCodeAndRoleNameIn(tenantCode, roleNames)
            .stream().map(p -> p.getPermission()).collect(Collectors.toSet());
    }
}
