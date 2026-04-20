package com.werkflow.admin.service;

import com.werkflow.admin.dto.DepartmentResponse;
import com.werkflow.admin.entity.Department;
import com.werkflow.admin.entity.Organization;
import com.werkflow.admin.repository.DepartmentRepository;
import com.werkflow.admin.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock DepartmentRepository deptRepo;
    @Mock OrganizationRepository orgRepo;
    @InjectMocks DepartmentService deptService;

    @Test
    void listsByTenantCode() {
        Organization org = new Organization();
        org.setId(1L);

        Department d = new Department();
        d.setId(1L);
        d.setName("Finance");
        d.setCode("FIN");
        d.setTenantCode("default");
        d.setOrganization(org);
        when(deptRepo.findByTenantCodeAndActiveTrue("default")).thenReturn(List.of(d));

        List<DepartmentResponse> result = deptService.listByTenant("default");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("FIN");
    }
}
