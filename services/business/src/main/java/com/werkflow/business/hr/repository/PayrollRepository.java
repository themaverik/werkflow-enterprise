package com.werkflow.business.hr.repository;

import com.werkflow.business.hr.entity.Payroll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Payroll entity
 */
@Repository
public interface PayrollRepository extends JpaRepository<Payroll, Long> {

    List<Payroll> findByEmployeeId(Long employeeId);

    Optional<Payroll> findByEmployeeIdAndPaymentMonthAndPaymentYear(Long employeeId, Integer month, Integer year);

    @Query("SELECT p FROM Payroll p WHERE p.employee.id = :employeeId " +
           "ORDER BY p.paymentYear DESC, p.paymentMonth DESC")
    List<Payroll> findByEmployeeIdOrderByPaymentDateDesc(@Param("employeeId") Long employeeId);

    @Query("SELECT p FROM Payroll p WHERE p.paymentMonth = :month AND p.paymentYear = :year")
    List<Payroll> findByMonthAndYear(@Param("month") Integer month, @Param("year") Integer year);

    @Query("SELECT p FROM Payroll p WHERE p.paymentDate BETWEEN :startDate AND :endDate")
    List<Payroll> findByPaymentDateBetween(@Param("startDate") LocalDate startDate,
                                           @Param("endDate") LocalDate endDate);

    List<Payroll> findByIsPaid(Boolean isPaid);

    @Query("SELECT p FROM Payroll p WHERE p.employee.department.id = :departmentId " +
           "AND p.paymentMonth = :month AND p.paymentYear = :year")
    List<Payroll> findByDepartmentAndMonthYear(@Param("departmentId") Long departmentId,
                                               @Param("month") Integer month,
                                               @Param("year") Integer year);

    @Query("SELECT SUM(p.netSalary) FROM Payroll p WHERE p.paymentMonth = :month " +
           "AND p.paymentYear = :year AND p.isPaid = true")
    Double getTotalPaidSalaryByMonthYear(@Param("month") Integer month, @Param("year") Integer year);

    @Query("SELECT SUM(p.netSalary) FROM Payroll p WHERE p.employee.department.id = :departmentId " +
           "AND p.paymentMonth = :month AND p.paymentYear = :year")
    Double getTotalSalaryByDepartmentAndMonthYear(@Param("departmentId") Long departmentId,
                                                   @Param("month") Integer month,
                                                   @Param("year") Integer year);

    boolean existsByEmployeeIdAndPaymentMonthAndPaymentYear(Long employeeId, Integer month, Integer year);
}
