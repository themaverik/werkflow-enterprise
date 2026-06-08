package com.werkflow.admin.repository;

import com.werkflow.admin.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByKeycloakId(String keycloakId);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByEmployeeId(String employeeId);

    List<User> findByOrganizationId(Long organizationId);

    Optional<User> findByIdAndTenantCode(Long id, String tenantCode);

    Optional<User> findByKeycloakIdAndTenantCode(String keycloakId, String tenantCode);

    Optional<User> findByUsernameAndTenantCode(String username, String tenantCode);

    List<User> findByOrganizationIdAndTenantCode(Long organizationId, String tenantCode);

    List<User> findByManagerId(Long managerId);

    @Query("SELECT u FROM User u WHERE u.organization.id = :orgId AND u.active = :active")
    List<User> findByOrganizationIdAndActive(@Param("orgId") Long orgId, @Param("active") Boolean active);

    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.id = :roleId")
    List<User> findByRoleId(@Param("roleId") Long roleId);

    boolean existsByKeycloakId(String keycloakId);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByEmployeeId(String employeeId);
}
