package com.werkflow.business.inventory.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "custody_records", schema = "inventory_service")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CustodyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_instance_id", nullable = false)
    private AssetInstance assetInstance;

    // Foreign key to admin_service.departments
    @Column(name = "custodian_dept_id", nullable = false)
    private Long custodianDeptId;

    // Foreign key to admin_service.users (optional - for individual assignment)
    @Column(name = "custodian_user_id")
    private Long custodianUserId;

    @Column(length = 200)
    private String physicalLocation;

    @Enumerated(EnumType.STRING)
    @Column(length = 50, nullable = false)
    @Builder.Default
    private CustodyType custodyType = CustodyType.PERMANENT;

    @Column(nullable = false)
    private LocalDateTime startDate;

    @Column
    private LocalDateTime endDate; // null = current custody

    // Foreign key to admin_service.users
    @Column(name = "assigned_by_user_id")
    private Long assignedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private AssetInstance.AssetCondition returnCondition;

    @Column(length = 1000)
    private String notes;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum CustodyType {
        PERMANENT,
        TEMPORARY,
        LOAN
    }
}
