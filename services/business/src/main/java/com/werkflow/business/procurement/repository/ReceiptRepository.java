package com.werkflow.business.procurement.repository;

import com.werkflow.business.procurement.entity.Receipt;
import com.werkflow.business.procurement.entity.Receipt.ReceiptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    Optional<Receipt> findByReceiptNumber(String receiptNumber);

    List<Receipt> findByPurchaseOrderId(Long purchaseOrderId);

    List<Receipt> findByStatus(ReceiptStatus status);

    List<Receipt> findByReceivedByUserId(Long userId);

    @Query("SELECT r FROM Receipt r WHERE r.status = 'DISCREPANCY'")
    List<Receipt> findReceiptsWithDiscrepancies();

    @Query("SELECT r FROM Receipt r WHERE r.receiptDate BETWEEN :startDate AND :endDate " +
           "ORDER BY r.receiptDate DESC")
    List<Receipt> findByReceiptDateRange(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query("SELECT r FROM Receipt r " +
           "WHERE r.purchaseOrder.vendor.id = :vendorId " +
           "ORDER BY r.receiptDate DESC")
    List<Receipt> findByVendorId(@Param("vendorId") Long vendorId);

    boolean existsByReceiptNumber(String receiptNumber);
}
