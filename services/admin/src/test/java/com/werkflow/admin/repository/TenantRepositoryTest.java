package com.werkflow.admin.repository;

import com.werkflow.admin.entity.Tenant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantRepositoryTest {

    @Mock
    TenantRepository repo;

    @Test
    void findByTenantCode_returnsMatchingTenant() {
        Tenant tenant = new Tenant();
        tenant.setTenantCode("default");
        tenant.setName("Default Organisation");
        tenant.setActive(true);

        when(repo.findByTenantCode("default")).thenReturn(Optional.of(tenant));

        Optional<Tenant> found = repo.findByTenantCode("default");
        assertThat(found).isPresent();
        assertThat(found.get().getTenantCode()).isEqualTo("default");
    }

    @Test
    void existsByTenantCode_returnsTrueWhenExists() {
        when(repo.existsByTenantCode("default")).thenReturn(true);
        assertThat(repo.existsByTenantCode("default")).isTrue();
    }
}
