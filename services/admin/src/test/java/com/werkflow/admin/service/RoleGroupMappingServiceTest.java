package com.werkflow.admin.service;

import com.werkflow.admin.dto.RoleGroupMappingRequest;
import com.werkflow.admin.dto.RoleGroupMappingResponse;
import com.werkflow.admin.entity.RoleGroupMapping;
import com.werkflow.admin.repository.RoleGroupMappingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleGroupMappingServiceTest {

    @Mock RoleGroupMappingRepository repository;
    @InjectMocks RoleGroupMappingService service;

    private RoleGroupMapping mapping(Long id, String tenant, String role, String group) {
        RoleGroupMapping m = new RoleGroupMapping();
        m.setId(id);
        m.setTenantCode(tenant);
        m.setRoleName(role);
        m.setGroupName(group);
        m.setCreatedAt(LocalDateTime.now());
        return m;
    }

    @Test
    void listByTenant_returnsOrderedMappings() {
        when(repository.findByTenantCodeOrderByRoleName("default")).thenReturn(List.of(
            mapping(1L, "default", "finance_approver", "DOA:L2"),
            mapping(2L, "default", "hr_approver",      "DOA:L1")
        ));

        List<RoleGroupMappingResponse> result = service.listByTenant("default");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).roleName()).isEqualTo("finance_approver");
        assertThat(result.get(1).roleName()).isEqualTo("hr_approver");
    }

    @Test
    void getGroupsByRole_returnsMergedMap() {
        when(repository.findByTenantCodeOrderByRoleName("default")).thenReturn(List.of(
            mapping(1L, "default", "finance_approver", "DOA:L2"),
            mapping(2L, "default", "finance_approver", "DOA:L1"),
            mapping(3L, "default", "hr_approver",      "DOA:L1")
        ));

        Map<String, List<String>> result = service.getGroupsByRole("default");

        assertThat(result).containsKey("finance_approver");
        assertThat(result.get("finance_approver")).containsExactlyInAnyOrder("DOA:L2", "DOA:L1");
        assertThat(result.get("hr_approver")).containsExactly("DOA:L1");
    }

    @Test
    void create_savesMapping_whenNotDuplicate() {
        RoleGroupMappingRequest req = new RoleGroupMappingRequest("default", "finance_approver", "DOA:L2");
        when(repository.existsByTenantCodeAndRoleNameAndGroupName("default", "finance_approver", "DOA:L2"))
            .thenReturn(false);
        RoleGroupMapping saved = mapping(5L, "default", "finance_approver", "DOA:L2");
        when(repository.save(any(RoleGroupMapping.class))).thenReturn(saved);

        RoleGroupMappingResponse result = service.create(req);

        assertThat(result.id()).isEqualTo(5L);
        assertThat(result.roleName()).isEqualTo("finance_approver");
        assertThat(result.groupName()).isEqualTo("DOA:L2");
    }

    @Test
    void delete_throwsWhenNotFound() {
        when(repository.existsById(99L)).thenReturn(false);
        assertThatThrownBy(() -> service.delete(99L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("99");
    }

    @Test
    void delete_deletesById_whenExists() {
        when(repository.existsById(1L)).thenReturn(true);
        service.delete(1L);
        verify(repository).deleteById(1L);
    }
}
