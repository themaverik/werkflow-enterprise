package com.werkflow.business.hr.repository;

import com.werkflow.business.hr.entity.Leave;
import com.werkflow.business.hr.entity.LeaveStatus;
import com.werkflow.business.hr.entity.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for Leave entity
 */
@Repository
public interface LeaveRepository extends JpaRepository<Leave, Long> {

    List<Leave> findByEmployeeId(Long employeeId);

    List<Leave> findByStatus(LeaveStatus status);

    List<Leave> findByLeaveType(LeaveType leaveType);

    @Query("SELECT l FROM Leave l WHERE l.employee.id = :employeeId AND l.status = :status")
    List<Leave> findByEmployeeIdAndStatus(@Param("employeeId") Long employeeId,
                                          @Param("status") LeaveStatus status);

    @Query("SELECT l FROM Leave l WHERE l.employee.id = :employeeId " +
           "AND l.startDate <= :endDate AND l.endDate >= :startDate")
    List<Leave> findOverlappingLeaves(@Param("employeeId") Long employeeId,
                                      @Param("startDate") LocalDate startDate,
                                      @Param("endDate") LocalDate endDate);

    @Query("SELECT l FROM Leave l WHERE l.startDate BETWEEN :startDate AND :endDate")
    List<Leave> findByDateRange(@Param("startDate") LocalDate startDate,
                                @Param("endDate") LocalDate endDate);

    @Query("SELECT SUM(l.numberOfDays) FROM Leave l WHERE l.employee.id = :employeeId " +
           "AND l.leaveType = :leaveType AND l.status = 'APPROVED' " +
           "AND EXTRACT(YEAR FROM l.startDate) = :year")
    Integer getTotalApprovedLeavesByType(@Param("employeeId") Long employeeId,
                                         @Param("leaveType") LeaveType leaveType,
                                         @Param("year") int year);

    List<Leave> findByApprovedById(Long approverId);

    long countByEmployeeIdAndStatus(Long employeeId, LeaveStatus status);
}
