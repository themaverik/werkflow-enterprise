package com.werkflow.business.hr.repository;

import com.werkflow.business.hr.entity.PerformanceRating;
import com.werkflow.business.hr.entity.PerformanceReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for PerformanceReview entity
 */
@Repository
public interface PerformanceReviewRepository extends JpaRepository<PerformanceReview, Long> {

    List<PerformanceReview> findByEmployeeId(Long employeeId);

    List<PerformanceReview> findByReviewerId(Long reviewerId);

    List<PerformanceReview> findByRating(PerformanceRating rating);

    @Query("SELECT pr FROM PerformanceReview pr WHERE pr.employee.id = :employeeId " +
           "ORDER BY pr.reviewDate DESC")
    List<PerformanceReview> findByEmployeeIdOrderByReviewDateDesc(@Param("employeeId") Long employeeId);

    @Query("SELECT pr FROM PerformanceReview pr WHERE pr.reviewDate BETWEEN :startDate AND :endDate")
    List<PerformanceReview> findByReviewDateBetween(@Param("startDate") LocalDate startDate,
                                                    @Param("endDate") LocalDate endDate);

    @Query("SELECT pr FROM PerformanceReview pr WHERE pr.employee.department.id = :departmentId " +
           "AND pr.reviewDate BETWEEN :startDate AND :endDate")
    List<PerformanceReview> findByDepartmentAndDateRange(@Param("departmentId") Long departmentId,
                                                         @Param("startDate") LocalDate startDate,
                                                         @Param("endDate") LocalDate endDate);

    @Query("SELECT AVG(pr.score) FROM PerformanceReview pr WHERE pr.employee.id = :employeeId")
    Double getAverageScoreByEmployee(@Param("employeeId") Long employeeId);

    @Query("SELECT pr FROM PerformanceReview pr WHERE pr.employeeAcknowledged = false")
    List<PerformanceReview> findPendingAcknowledgement();

    long countByEmployeeIdAndRating(Long employeeId, PerformanceRating rating);
}
