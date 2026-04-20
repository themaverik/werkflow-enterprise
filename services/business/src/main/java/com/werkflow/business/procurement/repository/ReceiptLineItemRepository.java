package com.werkflow.business.procurement.repository;

import com.werkflow.business.procurement.entity.ReceiptLineItem;
import com.werkflow.business.procurement.entity.ReceiptLineItem.ItemCondition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReceiptLineItemRepository extends JpaRepository<ReceiptLineItem, Long> {

    List<ReceiptLineItem> findByReceiptId(Long receiptId);

    List<ReceiptLineItem> findByPoLineItemId(Long poLineItemId);

    @Query("SELECT rli FROM ReceiptLineItem rli " +
           "WHERE rli.receipt.id = :receiptId " +
           "AND rli.condition != 'GOOD'")
    List<ReceiptLineItem> findItemsWithIssues(@Param("receiptId") Long receiptId);

    @Query("SELECT rli FROM ReceiptLineItem rli " +
           "WHERE rli.receipt.id = :receiptId " +
           "AND rli.rejectedQuantity > 0")
    List<ReceiptLineItem> findRejectedItems(@Param("receiptId") Long receiptId);

    @Query("SELECT SUM(rli.acceptedQuantity) FROM ReceiptLineItem rli " +
           "WHERE rli.poLineItem.id = :poLineItemId")
    Integer sumAcceptedQuantityByPoLineItem(@Param("poLineItemId") Long poLineItemId);
}
