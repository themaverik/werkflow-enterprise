package com.werkflow.admin.controller;

import com.werkflow.admin.dto.UserInviteRequest;
import com.werkflow.admin.dto.UserRequest;
import com.werkflow.admin.dto.UserResponse;
import com.werkflow.admin.dto.UserStatusRequest;
import com.werkflow.admin.dto.UserUpdateRequest;
import com.werkflow.admin.security.JwtClaimsExtractor;
import com.werkflow.admin.service.UserService;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User management APIs")
public class UserController {

    private final UserService userService;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @RateLimiter(name = "user-create", fallbackMethod = "createUserRateLimited")
    @Operation(summary = "Create user", description = "Create a new user (ADMIN, SUPER_ADMIN)")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserRequest request) {
        UserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/invite")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimiter(name = "user-invite", fallbackMethod = "inviteUserRateLimited")
    @Operation(summary = "Invite user", description = "Create KC user + admin DB row via email invite (ADMIN only)")
    public ResponseEntity<UserResponse> inviteUser(
            @Valid @RequestBody UserInviteRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String callerTenantCode = jwtClaimsExtractor.getTenantId(jwt);
        UserResponse response = userService.inviteUser(request, callerTenantCode);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Get user by ID", description = "Retrieve user details by ID (ADMIN, SUPER_ADMIN)")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        if (jwtClaimsExtractor.hasRole(jwt, "SUPER_ADMIN")) {
            return ResponseEntity.ok(userService.getUserById(id));
        }
        return ResponseEntity.ok(userService.getUserById(id, jwtClaimsExtractor.getTenantId(jwt)));
    }

    @GetMapping("/keycloak/{keycloakId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Get user by Keycloak ID", description = "Retrieve user details by Keycloak ID (ADMIN, SUPER_ADMIN)")
    public ResponseEntity<UserResponse> getUserByKeycloakId(@PathVariable String keycloakId,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwtClaimsExtractor.hasRole(jwt, "SUPER_ADMIN")) {
            return ResponseEntity.ok(userService.getUserByKeycloakId(keycloakId));
        }
        return ResponseEntity.ok(userService.getUserByKeycloakId(keycloakId, jwtClaimsExtractor.getTenantId(jwt)));
    }

    @GetMapping("/username/{username}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Get user by username", description = "Retrieve user details by username (ADMIN, SUPER_ADMIN)")
    public ResponseEntity<UserResponse> getUserByUsername(@PathVariable String username,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwtClaimsExtractor.hasRole(jwt, "SUPER_ADMIN")) {
            return ResponseEntity.ok(userService.getUserByUsername(username));
        }
        return ResponseEntity.ok(userService.getUserByUsername(username, jwtClaimsExtractor.getTenantId(jwt)));
    }

    @GetMapping("/organization/{organizationId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Get users by organization", description = "Retrieve all users for an organization (ADMIN, SUPER_ADMIN)")
    public ResponseEntity<List<UserResponse>> getUsersByOrganization(@PathVariable Long organizationId,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwtClaimsExtractor.hasRole(jwt, "SUPER_ADMIN")) {
            return ResponseEntity.ok(userService.getUsersByOrganization(organizationId));
        }
        return ResponseEntity.ok(userService.getUsersByOrganization(organizationId, jwtClaimsExtractor.getTenantId(jwt)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Update user", description = "Update name/doaLevel/departmentCode (ADMIN, SUPER_ADMIN)")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        boolean isSuperAdmin = jwtClaimsExtractor.hasRole(jwt, "SUPER_ADMIN");
        String tenantCode = isSuperAdmin ? null : jwtClaimsExtractor.getTenantId(jwt);
        UserResponse response = userService.updateUser(id, request, tenantCode);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Activate or deactivate user", description = "Toggle user active status (ADMIN, SUPER_ADMIN)")
    public ResponseEntity<UserResponse> updateUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody UserStatusRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        boolean isSuperAdmin = jwtClaimsExtractor.hasRole(jwt, "SUPER_ADMIN");
        String tenantCode = isSuperAdmin ? null : jwtClaimsExtractor.getTenantId(jwt);
        String callerKcId = jwt.getClaimAsString("preferred_username"); // DB keycloakId = email = preferred_username
        UserResponse response = Boolean.TRUE.equals(request.getActive())
                ? userService.reactivateUser(id, tenantCode, callerKcId)
                : userService.deactivateUser(id, tenantCode, callerKcId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Delete user", description = "Delete a user from DB and Keycloak (SUPER_ADMIN only)")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        String callerKcId = jwt.getClaimAsString("preferred_username"); // DB keycloakId = email = preferred_username
        userService.deleteUser(id, callerKcId);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<UserResponse> createUserRateLimited(UserRequest request, RequestNotPermitted ex) {
        return ResponseEntity.status(429).build();
    }

    private ResponseEntity<UserResponse> inviteUserRateLimited(UserInviteRequest request, Jwt jwt, RequestNotPermitted ex) {
        return ResponseEntity.status(429).build();
    }
}
