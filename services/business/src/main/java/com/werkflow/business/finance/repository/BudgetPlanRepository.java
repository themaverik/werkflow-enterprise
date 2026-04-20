package com.werkflow.business.finance.repository;

import com.werkflow.business.finance.entity.BudgetPlan;
import com.werkflow.business.finance.entity.BudgetPlan.BudgetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetPlanRepository extends JpaRepository<BudgetPlan, Long> {

    List<BudgetPlan> findByDepartmentId(Long departmentId);

    List<BudgetPlan> findByDepartmentIdAndStatus(Long departmentId, BudgetStatus status);

    Optional<BudgetPlan> findByDepartmentIdAndFiscalYear(Long departmentId, Integer fiscalYear);

    List<BudgetPlan> findByFiscalYear(Integer fiscalYear);

    List<BudgetPlan> findByStatus(BudgetStatus status);

    @Query("SELECT bp FROM BudgetPlan bp WHERE bp.departmentId = :deptId " +
           "AND bp.periodStart <= :date AND bp.periodEnd >= :date")
    Optional<BudgetPlan> findActiveBudgetForDepartment(
        @Param("deptId") Long departmentId,
        @Param("date") LocalDate date
    );

    @Query("SELECT bp FROM BudgetPlan bp WHERE bp.status = 'ACTIVE' " +
           "AND (bp.spentAmount / bp.totalAmount) > :threshold")
    List<BudgetPlan> findBudgetsExceedingThreshold(@Param("threshold") Double threshold);

    boolean existsByDepartmentIdAndFiscalYear(Long departmentId, Integer fiscalYear);
}
