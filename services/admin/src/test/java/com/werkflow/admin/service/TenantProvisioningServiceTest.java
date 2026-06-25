package com.werkflow.admin.service;

import com.werkflow.admin.dto.TenantProvisioningRequest;
import com.werkflow.admin.entity.Organization;
import com.werkflow.admin.entity.Role;
import com.werkflow.admin.entity.Tenant;
import com.werkflow.admin.entity.User;
import com.werkflow.admin.repository.OrganizationRepository;
import com.werkflow.admin.repository.RoleRepository;
import com.werkflow.admin.repository.TenantRepository;
import com.werkflow.admin.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantProvisioningServiceTest {

    @Mock TenantRepository tenantRepository;
    @Mock OrganizationRepository organizationRepository;
    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock KeycloakUserService keycloakUserService;
    @Mock ExampleSeedClient exampleSeedClient;

    @InjectMocks
    TenantProvisioningService service;

    private TenantProvisioningRequest request;

    @BeforeEach
    void setUp() {
        request = new TenantProvisioningRequest();
        request.setTenantCode("acme");
        request.setName("ACME Corp");
        request.setAdminEmail("admin@acme.com");
        request.setAdminFirstName("Alice");
        request.setAdminLastName("Admin");
    }

    @Test
    void provision_createsOrgAndAdminUserInDb() {
        when(tenantRepository.existsByTenantCode("acme")).thenReturn(false);
        Tenant savedTenant = new Tenant();
        savedTenant.setTenantCode("acme");
        when(tenantRepository.save(any(Tenant.class))).thenReturn(savedTenant);

        Organization savedOrg = mock(Organization.class);
        when(organizationRepository.save(any(Organization.class))).thenReturn(savedOrg);

        Role adminRole = new Role();
        adminRole.setName("ADMIN");
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));

        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.provision(request);

        // Org was saved with correct tenantCode
        ArgumentCaptor<Organization> orgCaptor = ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).save(orgCaptor.capture());
        assertThat(orgCaptor.getValue().getTenantCode()).isEqualTo("acme");
        assertThat(orgCaptor.getValue().getName()).isEqualTo("ACME Corp");

        // User was saved with email as keycloakId and username
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getKeycloakId()).isEqualTo("admin@acme.com");
        assertThat(savedUser.getUsername()).isEqualTo("admin@acme.com");
        assertThat(savedUser.getEmail()).isEqualTo("admin@acme.com");
        assertThat(savedUser.getTenantCode()).isEqualTo("acme");
        assertThat(savedUser.getDoaLevel()).isEqualTo(3);
        assertThat(savedUser.getRoles()).contains(adminRole);

        // KC user was created
        verify(keycloakUserService).createKeycloakUser(
                "admin@acme.com", "Alice", "Admin", "acme", "admin");
    }

    @Test
    void provision_compensatesAndThrows_whenKcFails() {
        when(tenantRepository.existsByTenantCode("acme")).thenReturn(false);
        Tenant savedTenant = new Tenant();
        when(tenantRepository.save(any(Tenant.class))).thenReturn(savedTenant);

        Organization savedOrg = mock(Organization.class);
        when(organizationRepository.save(any(Organization.class))).thenReturn(savedOrg);

        Role adminRole = new Role();
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        doThrow(new IllegalStateException("KC down"))
                .when(keycloakUserService).createKeycloakUser(anyString(), anyString(), anyString(), anyString(), anyString());

        assertThatThrownBy(() -> service.provision(request))
                .hasMessageContaining("Tenant provisioning failed");

        verify(keycloakUserService).createKeycloakUser(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(tenantRepository, never()).delete(any(Tenant.class));
        verify(organizationRepository, never()).delete(any(Organization.class));
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void provision_throwsConflict_whenTenantCodeExists() {
        when(tenantRepository.existsByTenantCode("acme")).thenReturn(true);
        assertThatThrownBy(() -> service.provision(request))
                .hasMessageContaining("already exists");
        verifyNoInteractions(organizationRepository, userRepository, keycloakUserService);
    }
}
