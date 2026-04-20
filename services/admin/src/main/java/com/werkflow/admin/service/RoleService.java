package com.werkflow.admin.service;

import com.werkflow.admin.dto.RoleRequest;
import com.werkflow.admin.dto.RoleResponse;
import com.werkflow.admin.entity.Organization;
import com.werkflow.admin.entity.Role;
import com.werkflow.admin.repository.OrganizationRepository;
import com.werkflow.admin.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleService {

    private final RoleRepository roleRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional
    public RoleResponse createRole(RoleRequest request) {
        log.info("Creating role: {} of type: {}", request.getName(), request.getType());

        if (roleRepository.existsByName(request.getName())) {
            throw new RuntimeException("Role with name '" + request.getName() + "' already exists");
        }

        Role.RoleBuilder builder = Role.builder()
            .name(request.getName())
            .description(request.getDescription())
            .type(request.getType())
            .permissions(request.getPermissions() != null ? request.getPermissions() : List.of())
            .active(request.getActive() != null ? request.getActive() : true);

        if (request.getOrganizationId() != null) {
            Organization organization = organizationRepository.findById(request.getOrganizationId())
                .orElseThrow(() -> new RuntimeException("Organization not found with ID: " + request.getOrganizationId()));
            builder.organization(organization);
        }

        Role role = builder.build();
        role = roleRepository.save(role);
        log.info("Role created successfully with ID: {}", role.getId());

        return mapToResponse(role);
    }

    @Transactional(readOnly = true)
    public RoleResponse getRoleById(Long id) {
        log.debug("Fetching role with ID: {}", id);

        Role role = roleRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Role not found with ID: " + id));

        return mapToResponse(role);
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> getAllRoles() {
        log.debug("Fetching all roles");

        return roleRepository.findAll().stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> getRolesByType(Role.RoleType type) {
        log.debug("Fetching roles by type: {}", type);

        return roleRepository.findByType(type).stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> getRolesByOrganization(Long organizationId) {
        log.debug("Fetching roles for organization ID: {}", organizationId);

        return roleRepository.findByOrganizationId(organizationId).stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public RoleResponse updateRole(Long id, RoleRequest request) {
        log.info("Updating role with ID: {}", id);

        Role role = roleRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Role not found with ID: " + id));

        if (!role.getName().equals(request.getName()) &&
            roleRepository.existsByName(request.getName())) {
            throw new RuntimeException("Role with name '" + request.getName() + "' already exists");
        }

        role.setName(request.getName());
        role.setDescription(request.getDescription());
        role.setType(request.getType());
        role.setPermissions(request.getPermissions() != null ? request.getPermissions() : List.of());
        if (request.getActive() != null) {
            role.setActive(request.getActive());
        }

        if (request.getOrganizationId() != null) {
            Organization organization = organizationRepository.findById(request.getOrganizationId())
                .orElseThrow(() -> new RuntimeException("Organization not found with ID: " + request.getOrganizationId()));
            role.setOrganization(organization);
        }

        role = roleRepository.save(role);
        log.info("Role updated successfully: {}", id);

        return mapToResponse(role);
    }

    @Transactional
    public void deleteRole(Long id) {
        log.info("Deleting role with ID: {}", id);

        if (!roleRepository.existsById(id)) {
            throw new RuntimeException("Role not found with ID: " + id);
        }

        roleRepository.deleteById(id);
        log.info("Role deleted successfully: {}", id);
    }

    private RoleResponse mapToResponse(Role role) {
        return RoleResponse.builder()
            .id(role.getId())
            .name(role.getName())
            .description(role.getDescription())
            .type(role.getType())
            .organizationId(role.getOrganization() != null ? role.getOrganization().getId() : null)
            .organizationName(role.getOrganization() != null ? role.getOrganization().getName() : null)
            .permissions(role.getPermissions())
            .active(role.getActive())
            .createdAt(role.getCreatedAt())
            .updatedAt(role.getUpdatedAt())
            .build();
    }
}
