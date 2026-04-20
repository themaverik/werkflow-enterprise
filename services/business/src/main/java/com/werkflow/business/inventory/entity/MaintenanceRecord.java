package com.werkflow.business.inventory.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "maintenance_records", schema = "inventory_service")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class MaintenanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_instance_id", nullable = false)
    private AssetInstance assetInstance;

    @Enumerated(EnumType.STRING)
    @Column(length = 50, nullable = false)
    private MaintenanceType maintenanceType;

    @Column
    private LocalDate scheduledDate;

    @Column
    private LocalDate completedDate;

    @Column(length = 200)
    private String performedBy;

    @Column(precision = 10, scale = 2)
    private BigDecimal cost;

    @Column(length = 2000)
    private String description;

    @Column
    private LocalDate nextMaintenanceDate;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum MaintenanceType {
        SCHEDULED,
        REPAIR,
        INSPECTION,
        CALIBRATION,
        UPGRADE
    }
}
