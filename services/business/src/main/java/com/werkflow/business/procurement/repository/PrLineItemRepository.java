package com.werkflow.business.procurement.repository;

import com.werkflow.business.procurement.entity.PrLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface PrLineItemRepository extends JpaRepository<PrLineItem, Long> {

    List<PrLineItem> findByPurchaseRequestId(Long purchaseRequestId);

    @Query("SELECT pli FROM PrLineItem pli WHERE pli.budgetCategoryId = :categoryId")
    List<PrLineItem> findByBudgetCategoryId(@Param("categoryId") Long budgetCategoryId);

    @Query("SELECT SUM(pli.totalPrice) FROM PrLineItem pli " +
           "WHERE pli.purchaseRequest.id = :prId")
    BigDecimal sumTotalPriceByPurchaseRequest(@Param("prId") Long purchaseRequestId);

    @Query("SELECT pli FROM PrLineItem pli " +
           "WHERE pli.purchaseRequest.id = :prId " +
           "ORDER BY pli.lineNumber ASC")
    List<PrLineItem> findByPurchaseRequestOrderByLineNumber(@Param("prId") Long purchaseRequestId);
}
