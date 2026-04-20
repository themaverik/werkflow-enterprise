package com.werkflow.admin.controller;

import com.werkflow.admin.dto.UserProfileResponse;
import com.werkflow.admin.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserService userService;

    @GetMapping("/{keycloakId}/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserProfileResponse> getProfile(
            @PathVariable String keycloakId,
            @RequestParam(required = false, defaultValue = "default") String tenantCode) {
        return ResponseEntity.ok(userService.getUserProfile(keycloakId, tenantCode));
    }
}
