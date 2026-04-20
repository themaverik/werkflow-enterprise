package com.werkflow.business.procurement.repository;

import com.werkflow.business.procurement.entity.PurchaseRequest;
import com.werkflow.business.procurement.entity.PurchaseRequest.Priority;
import com.werkflow.business.procurement.entity.PurchaseRequest.PrStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequest, Long> {

    Optional<PurchaseRequest> findByPrNumber(String prNumber);

    List<PurchaseRequest> findByRequestingDeptId(Long deptId);

    List<PurchaseRequest> findByRequesterUserId(Long userId);

    List<PurchaseRequest> findByStatus(PrStatus status);

    List<PurchaseRequest> findByStatusIn(List<PrStatus> statuses);

    @Query("SELECT pr FROM PurchaseRequest pr WHERE pr.requestingDeptId = :deptId " +
           "AND pr.status = :status ORDER BY pr.requestDate DESC")
    List<PurchaseRequest> findByDepartmentAndStatus(
        @Param("deptId") Long departmentId,
        @Param("status") PrStatus status
    );

    @Query("SELECT pr FROM PurchaseRequest pr WHERE pr.status IN ('SUBMITTED', 'PENDING_APPROVAL') " +
           "ORDER BY pr.priority DESC, pr.requestDate ASC")
    List<PurchaseRequest> findPendingRequests();

    @Query("SELECT pr FROM PurchaseRequest pr WHERE pr.status = 'PENDING_APPROVAL' " +
           "AND pr.priority = :priority")
    List<PurchaseRequest> findPendingByPriority(@Param("priority") Priority priority);

    @Query("SELECT pr FROM PurchaseRequest pr WHERE pr.requestDate BETWEEN :startDate AND :endDate " +
           "ORDER BY pr.requestDate DESC")
    List<PurchaseRequest> findByDateRange(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    boolean existsByPrNumber(String prNumber);
}
