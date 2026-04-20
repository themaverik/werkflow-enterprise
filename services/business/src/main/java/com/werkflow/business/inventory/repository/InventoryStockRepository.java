package com.werkflow.business.inventory.repository;

import com.werkflow.business.inventory.entity.InventoryStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryStockRepository extends JpaRepository<InventoryStock, Long> {
    List<InventoryStock> findByAssetDefinitionId(Long assetDefinitionId);
    Optional<InventoryStock> findByAssetDefinitionIdAndStockLocationId(Long defId, Long locationId);

    @Query("SELECT SUM(s.quantityAvailable) FROM InventoryStock s WHERE s.assetDefinition.id = :definitionId")
    Integer sumAvailableByDefinition(@Param("definitionId") Long definitionId);
}
