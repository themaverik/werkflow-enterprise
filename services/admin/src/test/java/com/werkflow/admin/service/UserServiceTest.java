package com.werkflow.admin.service;

import com.werkflow.admin.dto.UserRequest;
import com.werkflow.admin.dto.UserResponse;
import com.werkflow.admin.entity.Organization;
import com.werkflow.admin.entity.User;
import com.werkflow.admin.repository.OrganizationRepository;
import com.werkflow.admin.repository.RoleRepository;
import com.werkflow.admin.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock OrganizationRepository organizationRepository;
    @Mock RoleRepository roleRepository;
    @InjectMocks UserService userService;

    @Test
    void createUser_persistsTenantCodeAndDoaLevel() {
        Organization org = new Organization();
        org.setId(1L);
        org.setName("Acme");
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(org));
        when(userRepository.existsByKeycloakId(any())).thenReturn(false);
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(userRepository.existsByEmail(any())).thenReturn(false);

        User saved = new User();
        saved.setId(10L);
        saved.setKeycloakId("kc-1");
        saved.setUsername("alice");
        saved.setEmail("alice@acme.com");
        saved.setFirstName("Alice");
        saved.setLastName("Smith");
        saved.setOrganization(org);
        saved.setTenantCode("acme-corp");
        saved.setDoaLevel(2);
        when(userRepository.save(any())).thenReturn(saved);

        UserRequest req = new UserRequest();
        req.setKeycloakId("kc-1");
        req.setUsername("alice");
        req.setEmail("alice@acme.com");
        req.setFirstName("Alice");
        req.setLastName("Smith");
        req.setOrganizationId(1L);
        req.setTenantCode("acme-corp");
        req.setDoaLevel(2);

        UserResponse res = userService.createUser(req);
        assertThat(res.getTenantCode()).isEqualTo("acme-corp");
        assertThat(res.getDoaLevel()).isEqualTo(2);
    }
}
