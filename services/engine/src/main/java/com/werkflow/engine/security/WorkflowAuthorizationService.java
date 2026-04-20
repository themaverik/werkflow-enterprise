package com.werkflow.engine.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for workflow-specific authorization decisions.
 * Implements business rules for task assignment and approval routing.
 */
@Service
public class WorkflowAuthorizationService {

    private final KeycloakRoleExtractor roleExtractor;

    public WorkflowAuthorizationService(KeycloakRoleExtractor roleExtractor) {
        this.roleExtractor = roleExtractor;
    }

    /**
     * Get user context (for logging/audit)
     *
     * @param jwt User's JWT token
     * @return UserContext object
     */
    public UserContext getUserContext(Jwt jwt) {
        return new UserContext(
            roleExtractor.getUserId(jwt),
            roleExtractor.getUsername(jwt),
            roleExtractor.getUserEmail(jwt),
            roleExtractor.getFullName(jwt),
            roleExtractor.getDepartment(jwt),
            roleExtractor.getEmployeeId(jwt),
            roleExtractor.getDoaLevel(jwt),
            roleExtractor.isPoc(jwt),
            roleExtractor.extractRoleNames(jwt),
            roleExtractor.extractGroups(jwt)
        );
    }

    /**
     * User context record for passing around user information
     */
    public record UserContext(
        String userId,
        String username,
        String email,
        String fullName,
        String department,
        String employeeId,
        Integer doaLevel,
        boolean isPoc,
        List<String> roles,
        List<String> groups
    ) {
    }
}
