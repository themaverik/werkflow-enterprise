package com.werkflow.business.hr.repository;

import com.werkflow.business.hr.entity.Employee;
import com.werkflow.business.hr.entity.EmploymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Employee entity
 */
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmail(String email);

    Optional<Employee> findByKeycloakUserId(String keycloakUserId);

    List<Employee> findByOrganizationId(Long orgId);

    List<Employee> findByDepartmentCode(String code);

    List<Employee> findByOrganizationIdAndDepartmentCode(Long orgId, String code);

    List<Employee> findByDoaLevelGreaterThanEqual(Integer level);

    List<Employee> findByEmploymentStatus(EmploymentStatus status);

    // Backward-compat: query by FK department id via JPA navigation
    @Query("SELECT e FROM Employee e WHERE e.department.id = :departmentId")
    List<Employee> findByDepartmentId(@Param("departmentId") Long departmentId);

    @Query("SELECT e FROM Employee e WHERE e.department.id = :departmentId AND e.employmentStatus = :status")
    List<Employee> findByDepartmentIdAndStatus(@Param("departmentId") Long departmentId,
                                               @Param("status") EmploymentStatus status);

    @Query("SELECT e FROM Employee e WHERE LOWER(e.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(e.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(e.email) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Employee> searchEmployees(@Param("searchTerm") String searchTerm);

    @Query("SELECT e FROM Employee e WHERE e.dateOfJoining BETWEEN :startDate AND :endDate")
    List<Employee> findByJoinDateBetween(@Param("startDate") LocalDate startDate,
                                         @Param("endDate") LocalDate endDate);

    boolean existsByEmail(String email);

    boolean existsByKeycloakUserId(String keycloakUserId);

    // Used for department head uniqueness validation (doaLevel=2 means dept_head)
    boolean existsByDepartmentCodeAndDoaLevel(String code, Integer doaLevel);

    // Used on update path to exclude the employee being updated from the uniqueness check
    boolean existsByDepartmentCodeAndDoaLevelAndIdNot(String departmentCode, Integer doaLevel, Long id);

    long countByDepartmentCode(String code);

    long countByOrganizationId(Long orgId);

    long countByEmploymentStatus(EmploymentStatus status);
}
