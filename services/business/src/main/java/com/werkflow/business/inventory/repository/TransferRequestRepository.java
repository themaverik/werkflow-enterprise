package com.werkflow.business.inventory.repository;

import com.werkflow.business.inventory.entity.AssetInstance;
import com.werkflow.business.inventory.entity.TransferRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for TransferRequest entity
 */
@Repository
public interface TransferRequestRepository extends JpaRepository<TransferRequest, Long> {

    /**
     * Find transfer requests by asset instance
     */
    List<TransferRequest> findByAssetInstance(AssetInstance assetInstance);

    /**
     * Find transfer requests by status
     */
    List<TransferRequest> findByStatus(String status);

    /**
     * Find pending transfer requests
     */
    @Query("SELECT tr FROM TransferRequest tr WHERE tr.status = 'PENDING'")
    List<TransferRequest> findPendingRequests();

    /**
     * Find transfer requests by from department
     */
    List<TransferRequest> findByFromDeptId(Long fromDeptId);

    /**
     * Find transfer requests by to department
     */
    List<TransferRequest> findByToDeptId(Long toDeptId);

    /**
     * Find transfer requests by from department and status
     */
    List<TransferRequest> findByFromDeptIdAndStatus(Long fromDeptId, String status);

    /**
     * Find transfer requests by to department and status
     */
    List<TransferRequest> findByToDeptIdAndStatus(Long toDeptId, String status);

    /**
     * Find transfer requests by initiated user
     */
    List<TransferRequest> findByInitiatedByUserId(Long userId);

    /**
     * Find transfer requests by transfer type
     */
    List<TransferRequest> findByTransferType(String transferType);

    /**
     * Find inter-department transfers
     */
    @Query("SELECT tr FROM TransferRequest tr WHERE tr.transferType = 'INTER_DEPARTMENT' AND tr.status != 'CANCELLED'")
    List<TransferRequest> findActiveInterDepartmentTransfers();

    /**
     * Find loans that need to be returned
     */
    @Query("SELECT tr FROM TransferRequest tr WHERE tr.transferType = 'LOAN' AND tr.status = 'COMPLETED'")
    List<TransferRequest> findActiveLoanRequests();

    /**
     * Find overdue loans
     */
    @Query("SELECT tr FROM TransferRequest tr WHERE tr.transferType = 'LOAN' AND tr.expectedReturnDate <= :currentDate AND tr.status = 'APPROVED'")
    List<TransferRequest> findOverdueLoans(@Param("currentDate") LocalDateTime currentDate);

    /**
     * Find transfer requests by workflow instance ID
     */
    Optional<TransferRequest> findByProcessInstanceId(String processInstanceId);

    /**
     * Search transfer requests
     */
    @Query("SELECT tr FROM TransferRequest tr WHERE " +
           "LOWER(tr.transferReason) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR tr.assetInstance.assetTag LIKE CONCAT('%', :searchTerm, '%')")
    List<TransferRequest> searchTransfers(@Param("searchTerm") String searchTerm);
}
