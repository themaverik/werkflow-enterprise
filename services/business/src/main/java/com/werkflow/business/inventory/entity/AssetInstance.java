package com.werkflow.business.inventory.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "asset_instances", schema = "inventory_service")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class AssetInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_definition_id", nullable = false)
    private AssetDefinition assetDefinition;

    @Column(unique = true, nullable = false, length = 100)
    private String assetTag; // Barcode/QR code

    @Column(length = 100)
    private String serialNumber;

    @Column
    private LocalDate purchaseDate;

    @Column(precision = 10, scale = 2)
    private BigDecimal purchaseCost;

    @Column
    private LocalDate warrantyExpiryDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    @Builder.Default
    private AssetCondition condition = AssetCondition.NEW;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    @Builder.Default
    private AssetStatus status = AssetStatus.AVAILABLE;

    @Column(length = 200)
    private String currentLocation;

    @Column(length = 2000)
    private String notes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum AssetCondition {
        NEW,
        GOOD,
        FAIR,
        POOR,
        DAMAGED,
        NEEDS_REPAIR
    }

    public enum AssetStatus {
        AVAILABLE,
        IN_USE,
        MAINTENANCE,
        RETIRED,
        DISPOSED,
        LOST
    }
}
