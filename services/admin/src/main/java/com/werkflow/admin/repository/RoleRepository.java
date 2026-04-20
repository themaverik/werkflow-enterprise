package com.werkflow.admin.repository;

import com.werkflow.admin.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);

    List<Role> findByType(Role.RoleType type);

    List<Role> findByOrganizationId(Long organizationId);

    @Query("SELECT r FROM Role r WHERE r.organization.id = :orgId AND r.active = :active")
    List<Role> findByOrganizationIdAndActive(@Param("orgId") Long orgId, @Param("active") Boolean active);

    @Query("SELECT r FROM Role r WHERE r.type = :type AND r.active = true")
    List<Role> findActiveByType(@Param("type") Role.RoleType type);

    boolean existsByName(String name);
}
