package com.werkflow.business.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "inventory_stock",
    schema = "inventory_service",
    uniqueConstraints = @UniqueConstraint(columnNames = {"asset_definition_id", "stock_location_id"})
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_definition_id", nullable = false)
    private AssetDefinition assetDefinition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_location_id", nullable = false)
    private StockLocation stockLocation;

    @Builder.Default
    @Column(name = "quantity_total", nullable = false)
    private Integer quantityTotal = 0;

    @Builder.Default
    @Column(name = "quantity_available", nullable = false)
    private Integer quantityAvailable = 0;

    @Builder.Default
    @Column(name = "quantity_reserved", nullable = false)
    private Integer quantityReserved = 0;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @PrePersist
    @PreUpdate
    void updateTimestamp() {
        this.lastUpdated = LocalDateTime.now();
    }

    public boolean hasAvailableStock(int quantity) {
        return quantityAvailable >= quantity;
    }
}
