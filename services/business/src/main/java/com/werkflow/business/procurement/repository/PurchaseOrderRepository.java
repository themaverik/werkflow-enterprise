package com.werkflow.business.procurement.repository;

import com.werkflow.business.procurement.entity.PurchaseOrder;
import com.werkflow.business.procurement.entity.PurchaseOrder.PoStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByPoNumber(String poNumber);

    List<PurchaseOrder> findByVendorId(Long vendorId);

    List<PurchaseOrder> findByStatus(PoStatus status);

    List<PurchaseOrder> findByStatusIn(List<PoStatus> statuses);

    Optional<PurchaseOrder> findByPurchaseRequestId(Long purchaseRequestId);

    @Query("SELECT po FROM PurchaseOrder po WHERE po.vendor.id = :vendorId " +
           "AND po.status = :status ORDER BY po.orderDate DESC")
    List<PurchaseOrder> findByVendorAndStatus(
        @Param("vendorId") Long vendorId,
        @Param("status") PoStatus status
    );

    @Query("SELECT po FROM PurchaseOrder po WHERE po.status IN ('SENT', 'ACKNOWLEDGED', 'PARTIALLY_RECEIVED') " +
           "ORDER BY po.orderDate ASC")
    List<PurchaseOrder> findActivePurchaseOrders();

    @Query("SELECT po FROM PurchaseOrder po WHERE po.status = 'PARTIALLY_RECEIVED'")
    List<PurchaseOrder> findPartiallyReceivedOrders();

    @Query("SELECT po FROM PurchaseOrder po WHERE po.orderDate BETWEEN :startDate AND :endDate " +
           "ORDER BY po.orderDate DESC")
    List<PurchaseOrder> findByOrderDateRange(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query("SELECT po FROM PurchaseOrder po WHERE po.expectedDeliveryDate < :date " +
           "AND po.status NOT IN ('FULLY_RECEIVED', 'CLOSED', 'CANCELLED')")
    List<PurchaseOrder> findOverdueDeliveries(@Param("date") LocalDate date);

    boolean existsByPoNumber(String poNumber);
}
