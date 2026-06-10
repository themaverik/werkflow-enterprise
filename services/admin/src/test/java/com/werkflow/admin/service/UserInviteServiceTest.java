package com.werkflow.admin.service;

import com.werkflow.admin.dto.UserInviteRequest;
import com.werkflow.admin.entity.Organization;
import com.werkflow.admin.entity.Role;
import com.werkflow.admin.entity.User;
import com.werkflow.admin.repository.OrganizationRepository;
import com.werkflow.admin.repository.RoleRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserInviteServiceTest {

    @Mock UserRepository userRepository;
    @Mock OrganizationRepository organizationRepository;
    @Mock RoleRepository roleRepository;
    @Mock KeycloakUserService keycloakUserService;

    @InjectMocks
    UserService userService;

    private UserInviteRequest request;
    private Organization org;
    private Role employeeRole;

    @BeforeEach
    void setUp() {
        request = UserInviteRequest.builder()
                .email("jane@acme.com")
                .firstName("Jane")
                .lastName("Employee")
                .roleName("EMPLOYEE")
                .doaLevel(1)
                .departmentCode("HR")
                .build();

        org = mock(Organization.class);

        employeeRole = new Role();
        employeeRole.setName("EMPLOYEE");
    }

    @Test
    void inviteUser_createsKcAndDbUser() {
        when(org.getId()).thenReturn(1L);
        when(org.getName()).thenReturn("ACME Corp");
        when(organizationRepository.findByTenantCode("acme")).thenReturn(Optional.of(org));
        when(roleRepository.findByName("EMPLOYEE")).thenReturn(Optional.of(employeeRole));
        when(userRepository.existsByEmail("jane@acme.com")).thenReturn(false);
        when(userRepository.existsByKeycloakId("jane@acme.com")).thenReturn(false);
        when(userRepository.existsByUsername("jane@acme.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.inviteUser(request, "acme");

        verify(keycloakUserService).createKeycloakUser(
                "jane@acme.com", "Jane", "Employee", "acme", "employee");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getKeycloakId()).isEqualTo("jane@acme.com");
        assertThat(saved.getUsername()).isEqualTo("jane@acme.com");
        assertThat(saved.getEmail()).isEqualTo("jane@acme.com");
        assertThat(saved.getTenantCode()).isEqualTo("acme");
        assertThat(saved.getDoaLevel()).isEqualTo(1);
        assertThat(saved.getDepartmentCode()).isEqualTo("HR");
        assertThat(saved.getRoles()).contains(employeeRole);
    }

    @Test
    void inviteUser_compensatesDbRow_whenKcFails() {
        when(organizationRepository.findByTenantCode("acme")).thenReturn(Optional.of(org));
        when(roleRepository.findByName("EMPLOYEE")).thenReturn(Optional.of(employeeRole));
        when(userRepository.existsByEmail("jane@acme.com")).thenReturn(false);
        when(userRepository.existsByKeycloakId("jane@acme.com")).thenReturn(false);
        when(userRepository.existsByUsername("jane@acme.com")).thenReturn(false);
        User savedUser = mock(User.class);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        doThrow(new IllegalStateException("KC down"))
                .when(keycloakUserService).createKeycloakUser(anyString(), anyString(), anyString(), anyString(), anyString());

        assertThatThrownBy(() -> userService.inviteUser(request, "acme"))
                .hasMessageContaining("invite failed");

        verify(userRepository).delete(savedUser);
    }

    @Test
    void inviteUser_throwsConflict_whenEmailAlreadyExists() {
        when(userRepository.existsByEmail("jane@acme.com")).thenReturn(true);
        assertThatThrownBy(() -> userService.inviteUser(request, "acme"))
                .hasMessageContaining("already exists");
        verifyNoInteractions(keycloakUserService);
    }
}
