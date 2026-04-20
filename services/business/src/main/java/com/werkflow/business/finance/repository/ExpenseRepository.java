package com.werkflow.business.finance.repository;

import com.werkflow.business.finance.entity.Expense;
import com.werkflow.business.finance.entity.Expense.ExpenseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByDepartmentId(Long departmentId);

    List<Expense> findBySubmittedByUserId(Long userId);

    List<Expense> findByStatus(ExpenseStatus status);

    List<Expense> findByDepartmentIdAndStatus(Long departmentId, ExpenseStatus status);

    List<Expense> findByCategoryId(Long categoryId);

    List<Expense> findByBudgetLineItemId(Long budgetLineItemId);

    @Query("SELECT e FROM Expense e WHERE e.departmentId = :deptId " +
           "AND e.expenseDate BETWEEN :startDate AND :endDate")
    List<Expense> findByDepartmentAndDateRange(
        @Param("deptId") Long departmentId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query("SELECT SUM(e.amount) FROM Expense e WHERE e.departmentId = :deptId " +
           "AND e.status = 'APPROVED' " +
           "AND e.expenseDate BETWEEN :startDate AND :endDate")
    BigDecimal sumApprovedExpensesByDepartmentAndDateRange(
        @Param("deptId") Long departmentId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query("SELECT e FROM Expense e WHERE e.status IN ('SUBMITTED', 'PENDING_APPROVAL') " +
           "ORDER BY e.expenseDate DESC")
    List<Expense> findPendingExpenses();

    @Query("SELECT e FROM Expense e WHERE e.departmentId = :deptId " +
           "AND e.category.id = :categoryId " +
           "AND e.status = 'APPROVED' " +
           "AND e.expenseDate BETWEEN :startDate AND :endDate")
    List<Expense> findApprovedExpensesByDepartmentCategoryAndDateRange(
        @Param("deptId") Long departmentId,
        @Param("categoryId") Long categoryId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}
