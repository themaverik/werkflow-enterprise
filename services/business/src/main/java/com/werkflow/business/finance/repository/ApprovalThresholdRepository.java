package com.werkflow.business.finance.repository;

import com.werkflow.business.finance.entity.ApprovalThreshold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalThresholdRepository extends JpaRepository<ApprovalThreshold, Long> {

    List<ApprovalThreshold> findByActiveTrue();

    List<ApprovalThreshold> findByDepartmentId(Long departmentId);

    List<ApprovalThreshold> findByCategoryId(Long categoryId);

    @Query("SELECT at FROM ApprovalThreshold at WHERE at.active = true " +
           "AND (at.departmentId = :deptId OR at.departmentId IS NULL) " +
           "AND (at.category.id = :categoryId OR at.category IS NULL) " +
           "AND at.minAmount <= :amount " +
           "AND (at.maxAmount IS NULL OR at.maxAmount >= :amount) " +
           "ORDER BY at.departmentId DESC NULLS LAST, at.category.id DESC NULLS LAST")
    List<ApprovalThreshold> findApplicableThresholds(
        @Param("deptId") Long departmentId,
        @Param("categoryId") Long categoryId,
        @Param("amount") BigDecimal amount
    );

    @Query("SELECT at FROM ApprovalThreshold at WHERE at.active = true " +
           "AND at.departmentId IS NULL AND at.category IS NULL")
    List<ApprovalThreshold> findGlobalThresholds();
}
