package com.werkflow.admin.service;

import com.werkflow.admin.dto.RoleResponse;
import com.werkflow.admin.dto.UserInviteRequest;
import com.werkflow.admin.dto.UserProfileResponse;
import com.werkflow.admin.dto.UserRequest;
import com.werkflow.admin.dto.UserResponse;
import com.werkflow.admin.dto.UserStatusRequest;
import com.werkflow.admin.dto.UserUpdateRequest;
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
     * On KC failure, Spring rolls back the DB write automatically via {@code @Transactional}.
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

        if ("SUPER_ADMIN".equalsIgnoreCase(request.getRoleName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "SUPER_ADMIN role cannot be assigned via invite");
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
            // KC realm roles are lowercase by convention (e.g. "admin", "employee").
            // The DB roles table stores uppercase. toUpperCase() for DB, toLowerCase() for KC.
            keycloakUserService.createKeycloakUser(
                    request.getEmail(),
                    request.getFirstName(),
                    request.getLastName(),
                    callerTenantCode,
                    request.getRoleName().toLowerCase()
            );
        } catch (Exception e) {
            log.error("KC invite failed for email={}, transaction will roll back DB write: {}", request.getEmail(), e.getMessage());
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

    @Transactional
    public List<UserResponse> getUsersByOrganization(Long organizationId) {
        log.debug("Fetching users for organization ID: {}", organizationId);
        List<User> users = userRepository.findByOrganizationId(organizationId);
        syncEmailVerified(users);
        return users.stream().map(this::mapToResponse).collect(Collectors.toList());
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

    @Transactional
    public List<UserResponse> getUsersByOrganization(Long organizationId, String tenantCode) {
        log.debug("Fetching users for organization ID: {} for tenant: {}", organizationId, tenantCode);
        List<User> users = userRepository.findByOrganizationIdAndTenantCode(organizationId, tenantCode);
        syncEmailVerified(users);
        return users.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    /**
     * Best-effort sync: for any user with emailVerified=false locally, check KC.
     * If UPDATE_PASSWORD is no longer in their requiredActions, they've completed
     * registration — mark emailVerified=true in the DB.
     */
    private void syncEmailVerified(List<User> users) {
        users.stream()
            .filter(u -> !Boolean.TRUE.equals(u.getEmailVerified()))
            .forEach(u -> {
                try {
                    List<String> actions = keycloakUserService.getKcRequiredActions(u.getKeycloakId());
                    if (!actions.contains(KeycloakUserService.KC_ACTION_UPDATE_PASSWORD)) {
                        u.setEmailVerified(true);
                        userRepository.save(u);
                    }
                } catch (Exception e) {
                    log.debug("KC check skipped for user {}: {}", u.getKeycloakId(), e.getMessage());
                }
            });
    }

    /**
     * Loads a user by ID with optional tenant scope.
     * SUPER_ADMIN passes {@code null} tenantCode to bypass tenant isolation.
     */
    private User loadUser(Long id, String tenantCode) {
        if (tenantCode == null) {
            return userRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "User not found with ID: " + id));
        }
        return userRepository.findByIdAndTenantCode(id, tenantCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User not found with ID: " + id));
    }

    /**
     * Updates a user's editable fields (name, doaLevel, departmentCode) and syncs name to KC.
     * KC name sync failure is non-fatal — the DB update is always committed.
     *
     * @param id              user ID
     * @param request         fields to update
     * @param callerTenantCode tenant scope; null for SUPER_ADMIN
     */
    @Transactional
    public UserResponse updateUser(Long id, UserUpdateRequest request, String callerTenantCode) {
        log.info("Updating user with ID: {}", id);

        User user = loadUser(id, callerTenantCode);

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        if (request.getDoaLevel() != null) user.setDoaLevel(request.getDoaLevel());
        if (request.getDepartmentCode() != null) user.setDepartmentCode(request.getDepartmentCode());

        User saved = userRepository.save(user);
        log.info("User updated successfully: {}", id);

        try {
            keycloakUserService.updateKcUserName(user.getKeycloakId(), request.getFirstName(), request.getLastName());
        } catch (Exception e) {
            log.warn("KC name sync failed for user {}: {}", id, e.getMessage());
        }

        return mapToResponse(saved);
    }

    /**
     * Deactivates a user: disables their KC account and sets active=false in the DB.
     * Blocked if the user has not yet accepted their invitation (UPDATE_PASSWORD still pending).
     *
     * @param id               user ID
     * @param callerTenantCode tenant scope; null for SUPER_ADMIN
     * @param callerKeycloakId the caller's Keycloak subject (sub claim) — used for self-guard
     */
    @Transactional
    public UserResponse deactivateUser(Long id, String callerTenantCode, String callerKeycloakId) {
        User user = loadUser(id, callerTenantCode);

        if (user.getKeycloakId().equals(callerKeycloakId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Cannot deactivate your own account.");
        }

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "User is already inactive.");
        }

        List<String> requiredActions;
        try {
            requiredActions = keycloakUserService.getKcRequiredActions(user.getKeycloakId());
        } catch (Exception e) {
            log.error("KC unreachable when checking requiredActions for user {}: {}", user.getKeycloakId(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to verify user state in identity provider — please retry.");
        }
        if (requiredActions.contains(KeycloakUserService.KC_ACTION_UPDATE_PASSWORD)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "User has not yet accepted their invitation and cannot be deactivated.");
        }

        keycloakUserService.setKcUserEnabled(user.getKeycloakId(), false);

        // IMPORTANT: KC is now disabled. If the DB save below throws, the @Transactional
        // rollback cannot undo the KC call — the user will be disabled in KC but still
        // active=true in DB (split-brain). The error is logged here so ops can reconcile
        // via KC Admin Console. A compensating re-enable is NOT attempted to keep this path
        // simple; the recommended fix if this fires is to re-enable in KC and retry.
        try {
            user.setActive(false);
            User saved = userRepository.save(user);
            log.info("User {} deactivated", id);
            return mapToResponse(saved);
        } catch (Exception e) {
            log.error("SPLIT-BRAIN: KC user '{}' was disabled but DB save failed for user id={}. " +
                    "Manual reconciliation required — re-enable in KC or retry deactivation. Error: {}",
                    user.getKeycloakId(), id, e.getMessage());
            throw e;
        }
    }

    /**
     * Reactivates a user: enables their KC account and sets active=true in the DB.
     *
     * @param id               user ID
     * @param callerTenantCode tenant scope; null for SUPER_ADMIN
     * @param callerKeycloakId the caller's Keycloak subject (sub claim) — used for self-guard
     */
    @Transactional
    public UserResponse reactivateUser(Long id, String callerTenantCode, String callerKeycloakId) {
        User user = loadUser(id, callerTenantCode);

        if (user.getKeycloakId().equals(callerKeycloakId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Cannot modify your own account status.");
        }

        if (Boolean.TRUE.equals(user.getActive())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "User is already active.");
        }

        keycloakUserService.setKcUserEnabled(user.getKeycloakId(), true);

        // IMPORTANT: KC is now enabled. Same split-brain risk as deactivateUser — if the DB
        // save throws, KC is enabled but DB still shows active=false.
        try {
            user.setActive(true);
            User saved = userRepository.save(user);
            log.info("User {} reactivated", id);
            return mapToResponse(saved);
        } catch (Exception e) {
            log.error("SPLIT-BRAIN: KC user '{}' was re-enabled but DB save failed for user id={}. " +
                    "Manual reconciliation required — disable in KC or retry reactivation. Error: {}",
                    user.getKeycloakId(), id, e.getMessage());
            throw e;
        }
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

    /**
     * Deletes a user from the local DB and removes their Keycloak account.
     * KC deletion is non-fatal: if KC is unreachable or the user was already removed,
     * a warning is logged but the DB deletion is committed regardless.
     * SUPER_ADMIN only (no tenant scope check).
     *
     * <p>NOTE: active-task check via engine is not implemented here — no internal engine endpoint
     * exists for task-count-by-user. TODO: add check when engine exposes
     * GET /api/internal/users/{username}/active-task-count.
     *
     * @param id               user ID
     * @param callerKeycloakId the caller's KC subject — self-delete is blocked
     */
    @Transactional
    public void deleteUser(Long id, String callerKeycloakId) {
        log.info("Deleting user with ID: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User not found with ID: " + id));

        if (user.getKeycloakId().equals(callerKeycloakId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Cannot delete your own account.");
        }

        userRepository.deleteById(id);
        log.info("User {} ({}) deleted from local DB", user.getId(), user.getEmail());

        try {
            keycloakUserService.deleteKcUser(user.getKeycloakId());
        } catch (Exception e) {
            log.warn("KC delete failed for user {} ({}): {}", user.getId(), user.getEmail(), e.getMessage());
        }
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
            .departmentCode(user.getDepartmentCode())
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
