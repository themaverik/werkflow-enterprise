package com.werkflow.admin.service;

import com.werkflow.admin.entity.TenantRolePermission;
import com.werkflow.admin.repository.TenantRolePermissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantRolePermissionServiceTest {

    @Mock TenantRolePermissionRepository repo;
    @InjectMocks TenantRolePermissionService service;

    @Test
    void returnsPermissionsForMatchingRoles() {
        TenantRolePermission p1 = new TenantRolePermission();
        p1.setTenantCode("default"); p1.setRoleName("finance_approver"); p1.setPermission("TASK:VIEW_OWN");
        TenantRolePermission p2 = new TenantRolePermission();
        p2.setTenantCode("default"); p2.setRoleName("finance_approver"); p2.setPermission("PROCESS:VIEW_OWN");

        when(repo.findByTenantCodeAndRoleNameIn("default", List.of("finance_approver")))
            .thenReturn(List.of(p1, p2));

        Set<String> perms = service.getPermissionsForRoles("default", List.of("finance_approver"));
        assertThat(perms).containsExactlyInAnyOrder("TASK:VIEW_OWN", "PROCESS:VIEW_OWN");
    }

    @Test
    void returnsEmptySetForNullRoles() {
        assertThat(service.getPermissionsForRoles("default", null)).isEmpty();
    }

    @Test
    void returnsEmptySetForEmptyRoles() {
        assertThat(service.getPermissionsForRoles("default", List.of())).isEmpty();
    }
}
