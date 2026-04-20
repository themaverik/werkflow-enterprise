package com.werkflow.business.procurement.repository;

import com.werkflow.business.procurement.entity.PoLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface PoLineItemRepository extends JpaRepository<PoLineItem, Long> {

    List<PoLineItem> findByPurchaseOrderId(Long purchaseOrderId);

    @Query("SELECT poli FROM PoLineItem poli " +
           "WHERE poli.purchaseOrder.id = :poId " +
           "ORDER BY poli.lineNumber ASC")
    List<PoLineItem> findByPurchaseOrderOrderByLineNumber(@Param("poId") Long purchaseOrderId);

    @Query("SELECT poli FROM PoLineItem poli " +
           "WHERE poli.purchaseOrder.id = :poId " +
           "AND poli.receivedQuantity < poli.orderedQuantity")
    List<PoLineItem> findPendingReceiptItems(@Param("poId") Long purchaseOrderId);

    @Query("SELECT SUM(poli.totalPrice) FROM PoLineItem poli " +
           "WHERE poli.purchaseOrder.id = :poId")
    BigDecimal sumTotalPriceByPurchaseOrder(@Param("poId") Long purchaseOrderId);

    @Query("SELECT COUNT(poli) FROM PoLineItem poli " +
           "WHERE poli.purchaseOrder.id = :poId " +
           "AND poli.receivedQuantity >= poli.orderedQuantity")
    Long countFullyReceivedItems(@Param("poId") Long purchaseOrderId);

    @Query("SELECT COUNT(poli) FROM PoLineItem poli " +
           "WHERE poli.purchaseOrder.id = :poId")
    Long countTotalItems(@Param("poId") Long purchaseOrderId);
}
