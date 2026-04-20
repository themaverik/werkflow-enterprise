package com.werkflow.engine.security;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for interacting with Keycloak Admin API.
 * Provides methods to query users, roles, and groups.
 */
@Service
public class KeycloakUserService {

    private final Keycloak keycloak;
    private final String realm;

    public KeycloakUserService(
        Keycloak keycloak,
        @Value("${keycloak.realm:werkflow-platform}") String realm
    ) {
        this.keycloak = keycloak;
        this.realm = realm;
    }

    /**
     * Get RealmResource for current realm
     */
    private RealmResource getRealmResource() {
        return keycloak.realm(realm);
    }

    /**
     * Get UsersResource for current realm
     */
    private UsersResource getUsersResource() {
        return getRealmResource().users();
    }

    /**
     * Get user by ID
     *
     * @param userId Keycloak user ID
     * @return UserRepresentation
     */
    public UserRepresentation getUser(String userId) {
        return getUsersResource().get(userId).toRepresentation();
    }

    /**
     * Get user attribute value
     *
     * @param userId        Keycloak user ID
     * @param attributeName Attribute name
     * @return Attribute value, or null if not found
     */
    public String getUserAttribute(String userId, String attributeName) {
        UserRepresentation user = getUser(userId);
        if (user.getAttributes() == null) {
            return null;
        }

        List<String> values = user.getAttributes().get(attributeName);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    /**
     * Set user attribute value
     *
     * @param userId         Keycloak user ID
     * @param attributeName  Attribute name
     * @param attributeValue Attribute value
     */
    public void setUserAttribute(String userId, String attributeName, String attributeValue) {
        UserResource userResource = getUsersResource().get(userId);
        UserRepresentation user = userResource.toRepresentation();

        if (user.getAttributes() == null) {
            user.setAttributes(new java.util.HashMap<>());
        }

        user.getAttributes().put(attributeName, List.of(attributeValue));
        userResource.update(user);
    }

    /**
     * Check if user has specific realm role
     *
     * @param userId   Keycloak user ID
     * @param roleName Role name
     * @return true if user has role
     */
    public boolean hasRole(String userId, String roleName) {
        List<RoleRepresentation> roles = getUsersResource().get(userId).roles().realmLevel().listAll();
        return roles.stream().anyMatch(role -> role.getName().equals(roleName));
    }

    /**
     * Get all realm roles for user
     *
     * @param userId Keycloak user ID
     * @return List of role names
     */
    public List<String> getUserRoles(String userId) {
        return getUsersResource().get(userId).roles().realmLevel().listAll().stream()
            .map(RoleRepresentation::getName)
            .collect(Collectors.toList());
    }

    /**
     * Assign realm role to user
     *
     * @param userId   Keycloak user ID
     * @param roleName Role name
     */
    public void assignRole(String userId, String roleName) {
        RoleRepresentation role = getRealmResource().roles().get(roleName).toRepresentation();
        getUsersResource().get(userId).roles().realmLevel().add(List.of(role));
    }

    /**
     * Remove realm role from user
     *
     * @param userId   Keycloak user ID
     * @param roleName Role name
     */
    public void removeRole(String userId, String roleName) {
        RoleRepresentation role = getRealmResource().roles().get(roleName).toRepresentation();
        getUsersResource().get(userId).roles().realmLevel().remove(List.of(role));
    }

    /**
     * Get all users with specific role
     *
     * @param roleName Role name
     * @return List of user IDs
     */
    public List<String> getUsersWithRole(String roleName) {
        return getRealmResource().roles().get(roleName).getRoleUserMembers().stream()
            .map(UserRepresentation::getId)
            .collect(Collectors.toList());
    }

    /**
     * Get group by path
     *
     * @param groupPath Full group path (e.g., "/HR Department/Managers")
     * @return GroupRepresentation
     */
    public GroupRepresentation getGroupByPath(String groupPath) {
        return getRealmResource().getGroupByPath(groupPath);
    }

    /**
     * Get all members of a group
     *
     * @param groupPath Full group path
     * @return List of user IDs
     */
    public List<String> getGroupMembers(String groupPath) {
        GroupRepresentation group = getGroupByPath(groupPath);
        if (group == null) {
            return List.of();
        }

        return getRealmResource().groups().group(group.getId()).members().stream()
            .map(UserRepresentation::getId)
            .collect(Collectors.toList());
    }

    /**
     * Add user to group
     *
     * @param userId    Keycloak user ID
     * @param groupPath Full group path
     */
    public void joinGroup(String userId, String groupPath) {
        GroupRepresentation group = getGroupByPath(groupPath);
        if (group != null) {
            getUsersResource().get(userId).joinGroup(group.getId());
        }
    }

    /**
     * Remove user from group
     *
     * @param userId    Keycloak user ID
     * @param groupPath Full group path
     */
    public void leaveGroup(String userId, String groupPath) {
        GroupRepresentation group = getGroupByPath(groupPath);
        if (group != null) {
            getUsersResource().get(userId).leaveGroup(group.getId());
        }
    }

    /**
     * Get all groups for user
     *
     * @param userId Keycloak user ID
     * @return List of group paths
     */
    public List<String> getUserGroups(String userId) {
        return getUsersResource().get(userId).groups().stream()
            .map(GroupRepresentation::getPath)
            .collect(Collectors.toList());
    }

    /**
     * Search users by attribute
     *
     * @param attributeName  Attribute name
     * @param attributeValue Attribute value
     * @return List of user IDs
     */
    public List<String> searchUsersByAttribute(String attributeName, String attributeValue) {
        // Keycloak doesn't support direct attribute search via API
        // Need to fetch all users and filter (not efficient for large user bases)
        return getUsersResource().list().stream()
            .filter(user -> {
                if (user.getAttributes() == null) {
                    return false;
                }
                List<String> values = user.getAttributes().get(attributeName);
                return values != null && values.contains(attributeValue);
            })
            .map(UserRepresentation::getId)
            .collect(Collectors.toList());
    }

    /**
     * Find users by department
     *
     * @param department Department name
     * @return List of user IDs
     */
    public List<String> getUsersByDepartment(String department) {
        return searchUsersByAttribute("department", department);
    }

    /**
     * Find users by DOA level
     *
     * @param doaLevel DOA level (1-4)
     * @return List of user IDs
     */
    public List<String> getUsersByDoaLevel(int doaLevel) {
        return searchUsersByAttribute("doa_level", String.valueOf(doaLevel));
    }

    /**
     * Create new user
     *
     * @param username  Username
     * @param email     Email
     * @param firstName First name
     * @param lastName  Last name
     * @param password  Initial password
     * @return Created user ID
     */
    public String createUser(String username, String email, String firstName, String lastName, String password) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEnabled(true);
        user.setEmailVerified(true);

        // Create user
        getUsersResource().create(user);

        // Find created user
        List<UserRepresentation> users = getUsersResource().search(username);
        String userId = users.get(0).getId();

        // Set password
        setPassword(userId, password, false);

        return userId;
    }

    /**
     * Set user password
     *
     * @param userId    Keycloak user ID
     * @param password  New password
     * @param temporary Whether password is temporary
     */
    public void setPassword(String userId, String password, boolean temporary) {
        org.keycloak.representations.idm.CredentialRepresentation credential =
            new org.keycloak.representations.idm.CredentialRepresentation();
        credential.setType(org.keycloak.representations.idm.CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(temporary);

        getUsersResource().get(userId).resetPassword(credential);
    }

    /**
     * Disable user account
     *
     * @param userId Keycloak user ID
     */
    public void disableUser(String userId) {
        UserResource userResource = getUsersResource().get(userId);
        UserRepresentation user = userResource.toRepresentation();
        user.setEnabled(false);
        userResource.update(user);
    }

    /**
     * Enable user account
     *
     * @param userId Keycloak user ID
     */
    public void enableUser(String userId) {
        UserResource userResource = getUsersResource().get(userId);
        UserRepresentation user = userResource.toRepresentation();
        user.setEnabled(true);
        userResource.update(user);
    }

    /**
     * Logout all sessions for user
     *
     * @param userId Keycloak user ID
     */
    public void logoutUser(String userId) {
        getUsersResource().get(userId).logout();
    }

    /**
     * Get all top-level groups in the realm.
     *
     * @return List of GroupRepresentation
     */
    public List<GroupRepresentation> getAllGroups() {
        return getRealmResource().groups().groups(0, 200);
    }
}
