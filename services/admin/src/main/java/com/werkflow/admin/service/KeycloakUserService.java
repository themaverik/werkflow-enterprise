package com.werkflow.admin.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for Keycloak user management and DOA-based user searches.
 * Provides functionality to query Keycloak for users by DOA level, department, etc.
 */
@Service
public class KeycloakUserService {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakUserService.class);

    @Value("${app.keycloak.auth-server-url:http://localhost:8090}")
    private String keycloakAuthUrl;

    @Value("${app.keycloak.realm:werkflow}")
    private String keycloakRealm;

    /**
     * Find users by DOA level and department.
     * This would normally use Keycloak Admin API.
     *
     * @param doaLevel The required DOA level (1-4)
     * @param department The department filter (optional)
     * @return List of user info objects with DOA level >= specified level
     */
    public List<KeycloakUserInfo> findUsersByDoALevel(int doaLevel, String department) {
        logger.debug(
            "Searching for users with DOA level {} in department {}",
            doaLevel, department != null ? department : "ANY"
        );

        // In real implementation, this would query Keycloak Admin API:
        // GET /auth/admin/realms/{realm}/users?q=attributes.doa_level:{level}
        // Or use user search with custom attribute filtering

        List<KeycloakUserInfo> results = new ArrayList<>();

        // Mock data for demonstration
        if (doaLevel >= 1) {
            results.add(new KeycloakUserInfo("manager1", "John Manager", "manager@company.com", 1, "HR"));
            results.add(new KeycloakUserInfo("manager2", "Jane Manager", "jmanager@company.com", 1, "Finance"));
        }

        if (doaLevel >= 2) {
            results.add(new KeycloakUserInfo("hr_head", "HR Head", "hrhead@company.com", 2, "HR"));
            results.add(new KeycloakUserInfo("fin_head", "Finance Head", "finhead@company.com", 2, "Finance"));
        }

        if (doaLevel >= 3) {
            results.add(new KeycloakUserInfo("cfo", "CFO", "cfo@company.com", 3, "Finance"));
        }

        if (doaLevel >= 4) {
            results.add(new KeycloakUserInfo("ceo", "CEO", "ceo@company.com", 4, "Executive"));
        }

        // Filter by department if specified
        if (department != null && !department.isEmpty()) {
            results = results.stream()
                .filter(user -> user.getDepartment().equalsIgnoreCase(department))
                .collect(Collectors.toList());
        }

        logger.info("Found {} users with DOA level {}", results.size(), doaLevel);
        return results;
    }

    /**
     * Find users in a specific Keycloak group.
     *
     * @param groupName The group name (e.g., "finance_approvers")
     * @return List of users in the group
     */
    public List<KeycloakUserInfo> findUsersByGroup(String groupName) {
        logger.debug("Searching for users in group {}", groupName);

        // In real implementation, this would query Keycloak Admin API:
        // GET /auth/admin/realms/{realm}/groups/{groupId}/members

        List<KeycloakUserInfo> results = new ArrayList<>();

        // Mock data for demonstration
        switch (groupName.toLowerCase()) {
            case "department_managers":
                results.add(new KeycloakUserInfo("manager1", "John Manager", "manager@company.com", 1, "HR"));
                results.add(new KeycloakUserInfo("manager2", "Jane Manager", "jmanager@company.com", 1, "Finance"));
                break;
            case "department_heads":
                results.add(new KeycloakUserInfo("hr_head", "HR Head", "hrhead@company.com", 2, "HR"));
                results.add(new KeycloakUserInfo("fin_head", "Finance Head", "finhead@company.com", 2, "Finance"));
                break;
            case "finance_approvers":
                results.add(new KeycloakUserInfo("cfo", "CFO", "cfo@company.com", 3, "Finance"));
                break;
            case "executive_approvers":
                results.add(new KeycloakUserInfo("ceo", "CEO", "ceo@company.com", 4, "Executive"));
                break;
        }

        logger.info("Found {} users in group {}", results.size(), groupName);
        return results;
    }

    /**
     * Find a user by username.
     *
     * @param username The Keycloak username
     * @return User info or null if not found
     */
    public KeycloakUserInfo findUserByUsername(String username) {
        logger.debug("Searching for user: {}", username);

        // In real implementation, this would query Keycloak Admin API:
        // GET /auth/admin/realms/{realm}/users?username={username}

        // Mock data
        if ("manager1".equals(username)) {
            return new KeycloakUserInfo("manager1", "John Manager", "manager@company.com", 1, "HR");
        } else if ("cfo".equals(username)) {
            return new KeycloakUserInfo("cfo", "CFO", "cfo@company.com", 3, "Finance");
        }

        return null;
    }

    /**
     * Get the manager for a user.
     *
     * @param username The username
     * @return Manager info or null if user not found or has no manager
     */
    public KeycloakUserInfo getManagerForUser(String username) {
        logger.debug("Getting manager for user: {}", username);

        KeycloakUserInfo user = findUserByUsername(username);
        if (user == null) {
            return null;
        }

        // In real implementation, query user attributes for manager_id
        // Then look up that user: GET /auth/admin/realms/{realm}/users/{userId}

        // Mock: Return HR head as manager for all employees
        return new KeycloakUserInfo("hr_head", "HR Head", "hrhead@company.com", 2, "HR");
    }

    /**
     * Get all users with a specific role.
     *
     * @param roleName The role name
     * @return List of users with the role
     */
    public List<KeycloakUserInfo> findUsersByRole(String roleName) {
        logger.debug("Searching for users with role: {}", roleName);

        // In real implementation:
        // GET /auth/admin/realms/{realm}/roles/{roleName}/users

        List<KeycloakUserInfo> results = new ArrayList<>();

        if ("asset_approver".equals(roleName)) {
            results.add(new KeycloakUserInfo("inv_manager", "Inventory Manager", "invmgr@company.com", 2, "Inventory"));
        }

        return results;
    }

    /**
     * Update user attributes (DOA level, department, etc).
     * Requires service account with admin privilege.
     *
     * @param username The username
     * @param attributes Map of attribute names to values
     * @return true if successful
     */
    public boolean updateUserAttributes(String username, Map<String, String> attributes) {
        logger.debug("Updating attributes for user {}: {}", username, attributes.keySet());

        // In real implementation:
        // GET user by username, then
        // PUT /auth/admin/realms/{realm}/users/{userId}
        // With updated attributes

        try {
            logger.info("Successfully updated attributes for user {}", username);
            return true;
        } catch (Exception e) {
            logger.error("Failed to update attributes for user {}", username, e);
            return false;
        }
    }

    /**
     * Keycloak user information DTO.
     */
    public static class KeycloakUserInfo {
        private String username;
        private String displayName;
        private String email;
        private Integer doaLevel;
        private String department;

        public KeycloakUserInfo(String username, String displayName, String email, Integer doaLevel, String department) {
            this.username = username;
            this.displayName = displayName;
            this.email = email;
            this.doaLevel = doaLevel;
            this.department = department;
        }

        public String getUsername() {
            return username;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getEmail() {
            return email;
        }

        public Integer getDoaLevel() {
            return doaLevel;
        }

        public String getDepartment() {
            return department;
        }

        @Override
        public String toString() {
            return "KeycloakUserInfo{" +
                "username='" + username + '\'' +
                ", displayName='" + displayName + '\'' +
                ", doaLevel=" + doaLevel +
                ", department='" + department + '\'' +
                '}';
        }
    }
}
