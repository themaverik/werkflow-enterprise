package com.werkflow.admin.service;

import com.werkflow.admin.dto.RoleResponse;
import com.werkflow.admin.dto.UserInviteRequest;
import com.werkflow.admin.dto.UserProfileResponse;
import com.werkflow.admin.dto.UserRequest;
import com.werkflow.admin.dto.UserResponse;
import com.werkflow.admin.entity.Organization;
import com.werkflow.admin.entity.Role;
import com.werkflow.admin.entity.User;
import com.werkflow.admin.repository.OrganizationRepository;
import com.werkflow.admin.repository.RoleRepository;
import com.werkflow.admin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final RoleRepository roleRepository;
    private final KeycloakUserService keycloakUserService;

    @Transactional
    public UserResponse createUser(UserRequest request) {
        log.info("Creating user: {} ({})", request.getUsername(), request.getEmail());

        if (userRepository.existsByKeycloakId(request.getKeycloakId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "User with Keycloak ID '" + request.getKeycloakId() + "' already exists");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "User with username '" + request.getUsername() + "' already exists");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "User with email '" + request.getEmail() + "' already exists");
        }

        Organization organization = organizationRepository.findById(request.getOrganizationId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Organization not found with ID: " + request.getOrganizationId()));

        User.UserBuilder builder = User.builder()
            .keycloakId(request.getKeycloakId())
            .username(request.getUsername())
            .email(request.getEmail())
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .phone(request.getPhone())
            .mobile(request.getMobile())
            .organization(organization)
            .jobTitle(request.getJobTitle())
            .employeeId(request.getEmployeeId())
            .hireDate(request.getHireDate())
            .address(request.getAddress())
            .city(request.getCity())
            .state(request.getState())
            .country(request.getCountry())
            .postalCode(request.getPostalCode())
            .active(request.getActive() != null ? request.getActive() : true)
            .emailVerified(request.getEmailVerified() != null ? request.getEmailVerified() : false)
            .tenantCode(request.getTenantCode())
            .doaLevel(request.getDoaLevel());

        if (request.getManagerId() != null) {
            User manager = userRepository.findById(request.getManagerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Manager not found with ID: " + request.getManagerId()));
            builder.manager(manager);
        }

        User user = builder.build();

        if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            List<Role> roles = roleRepository.findAllById(request.getRoleIds());
            user.setRoles(roles);
        }

        user = userRepository.save(user);
        log.info("User created successfully with ID: {}", user.getId());

        return mapToResponse(user);
    }

    /**
     * Invites a new user: writes the admin DB row first, then creates the Keycloak user.
     * On KC failure, the DB row is deleted (compensating action).
     *
     * <p>KC username = email = DB {@code keycloak_id} = DB {@code username} (preferred_username contract).
     *
     * @param request        invite details — email becomes the KC username/preferred_username
     * @param callerTenantCode tenant code extracted from the caller's JWT
     * @return {@link UserResponse} for the newly created user
     * @throws ResponseStatusException 409 if email/username/keycloakId already exists; 500 on KC failure
     */
    @Transactional
    public UserResponse inviteUser(UserInviteRequest request, String callerTenantCode) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "User with email '" + request.getEmail() + "' already exists");
        }
        if (userRepository.existsByKeycloakId(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "User with Keycloak ID '" + request.getEmail() + "' already exists");
        }
        if (userRepository.existsByUsername(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "User with username '" + request.getEmail() + "' already exists");
        }

        Organization org = organizationRepository.findByTenantCode(callerTenantCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Organization not found for tenant: " + callerTenantCode));

        Role role = roleRepository.findByName(request.getRoleName().toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Role not found: " + request.getRoleName()));

        // keycloakId = username = email — preferred_username contract
        User user = User.builder()
                .keycloakId(request.getEmail())
                .username(request.getEmail())
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .organization(org)
                .tenantCode(callerTenantCode)
                .doaLevel(request.getDoaLevel())
                .departmentCode(request.getDepartmentCode())
                .active(true)
                .emailVerified(false)
                .roles(List.of(role))
                .build();
        User saved = userRepository.save(user);

        try {
            keycloakUserService.createKeycloakUser(
                    request.getEmail(),
                    request.getFirstName(),
                    request.getLastName(),
                    callerTenantCode,
                    request.getRoleName().toLowerCase()
            );
        } catch (Exception e) {
            log.error("KC invite failed for email={}, compensating DB delete: {}", request.getEmail(), e.getMessage());
            userRepository.delete(saved);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "User invite failed: Keycloak user could not be created");
        }

        log.info("User invited: email={}, tenant={}, role={}", request.getEmail(), callerTenantCode, request.getRoleName());
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        log.debug("Fetching user with ID: {}", id);

        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "User not found with ID: " + id));

        return mapToResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByKeycloakId(String keycloakId) {
        log.debug("Fetching user with Keycloak ID: {}", keycloakId);

        User user = userRepository.findByKeycloakId(keycloakId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "User not found with Keycloak ID: " + keycloakId));

        return mapToResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByUsername(String username) {
        log.debug("Fetching user with username: {}", username);

        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "User not found with username: " + username));

        return mapToResponse(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getUsersByOrganization(Long organizationId) {
        log.debug("Fetching users for organization ID: {}", organizationId);

        return userRepository.findByOrganizationId(organizationId).stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id, String tenantCode) {
        log.debug("Fetching user with ID: {} for tenant: {}", id, tenantCode);

        User user = userRepository.findByIdAndTenantCode(id, tenantCode)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "User not found with ID: " + id));

        return mapToResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByKeycloakId(String keycloakId, String tenantCode) {
        log.debug("Fetching user with Keycloak ID: {} for tenant: {}", keycloakId, tenantCode);

        User user = userRepository.findByKeycloakIdAndTenantCode(keycloakId, tenantCode)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "User not found with Keycloak ID: " + keycloakId));

        return mapToResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByUsername(String username, String tenantCode) {
        log.debug("Fetching user with username: {} for tenant: {}", username, tenantCode);

        User user = userRepository.findByUsernameAndTenantCode(username, tenantCode)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "User not found with username: " + username));

        return mapToResponse(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getUsersByOrganization(Long organizationId, String tenantCode) {
        log.debug("Fetching users for organization ID: {} for tenant: {}", organizationId, tenantCode);

        return userRepository.findByOrganizationIdAndTenantCode(organizationId, tenantCode)
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public UserResponse updateUser(Long id, UserRequest request) {
        log.info("Updating user with ID: {}", id);

        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "User not found with ID: " + id));

        if (!user.getUsername().equals(request.getUsername()) &&
            userRepository.existsByUsername(request.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "User with username '" + request.getUsername() + "' already exists");
        }

        if (!user.getEmail().equals(request.getEmail()) &&
            userRepository.existsByEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "User with email '" + request.getEmail() + "' already exists");
        }

        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPhone(request.getPhone());
        user.setMobile(request.getMobile());
        user.setJobTitle(request.getJobTitle());
        user.setEmployeeId(request.getEmployeeId());
        user.setHireDate(request.getHireDate());
        user.setAddress(request.getAddress());
        user.setCity(request.getCity());
        user.setState(request.getState());
        user.setCountry(request.getCountry());
        user.setPostalCode(request.getPostalCode());
        if (request.getTenantCode() != null) user.setTenantCode(request.getTenantCode());
        if (request.getDoaLevel() != null) user.setDoaLevel(request.getDoaLevel());
        if (request.getActive() != null) {
            user.setActive(request.getActive());
        }
        if (request.getEmailVerified() != null) {
            user.setEmailVerified(request.getEmailVerified());
        }

        if (request.getManagerId() != null) {
            User manager = userRepository.findById(request.getManagerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Manager not found with ID: " + request.getManagerId()));
            user.setManager(manager);
        }

        if (request.getRoleIds() != null) {
            List<Role> roles = roleRepository.findAllById(request.getRoleIds());
            user.setRoles(roles);
        }

        user = userRepository.save(user);
        log.info("User updated successfully: {}", id);

        return mapToResponse(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        log.info("Deleting user with ID: {}", id);

        if (!userRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with ID: " + id);
        }

        userRepository.deleteById(id);
        log.info("User deleted successfully: {}", id);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(String keycloakId, String tenantCode) {
        User user = userRepository.findByKeycloakId(keycloakId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "User not found: " + keycloakId));
        return UserProfileResponse.builder()
            .keycloakId(keycloakId)
            .tenantCode(user.getTenantCode() != null ? user.getTenantCode() : tenantCode)
            .doaLevel(user.getDoaLevel())
            .departmentCode(user.getDepartmentCode())
            .build();
    }

    private UserResponse mapToResponse(User user) {
        return UserResponse.builder()
            .id(user.getId())
            .keycloakId(user.getKeycloakId())
            .username(user.getUsername())
            .email(user.getEmail())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .phone(user.getPhone())
            .mobile(user.getMobile())
            .organizationId(user.getOrganization().getId())
            .organizationName(user.getOrganization().getName())
            .jobTitle(user.getJobTitle())
            .employeeId(user.getEmployeeId())
            .managerId(user.getManager() != null ? user.getManager().getId() : null)
            .managerName(user.getManager() != null ?
                user.getManager().getFirstName() + " " + user.getManager().getLastName() : null)
            .hireDate(user.getHireDate())
            .address(user.getAddress())
            .city(user.getCity())
            .state(user.getState())
            .country(user.getCountry())
            .postalCode(user.getPostalCode())
            .roles(user.getRoles().stream()
                .map(this::mapRoleToResponse)
                .collect(Collectors.toList()))
            .active(user.getActive())
            .emailVerified(user.getEmailVerified())
            .lastLoginAt(user.getLastLoginAt())
            .createdAt(user.getCreatedAt())
            .updatedAt(user.getUpdatedAt())
            .tenantCode(user.getTenantCode())
            .doaLevel(user.getDoaLevel())
            .build();
    }

    private RoleResponse mapRoleToResponse(Role role) {
        return RoleResponse.builder()
            .id(role.getId())
            .name(role.getName())
            .description(role.getDescription())
            .type(role.getType())
            .permissions(role.getPermissions())
            .active(role.getActive())
            .build();
    }
}
