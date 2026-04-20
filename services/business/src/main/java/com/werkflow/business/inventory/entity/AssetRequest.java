package com.werkflow.business.inventory.entity;

import com.werkflow.business.hr.entity.OfficeLocation;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "asset_requests", schema = "inventory_service")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class AssetRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "process_instance_id", unique = true, length = 255)
    private String processInstanceId;

    @Column(name = "requester_user_id", nullable = false, length = 100)
    private String requesterUserId;

    @Column(name = "requester_name", nullable = false, length = 200)
    private String requesterName;

    @Column(name = "requester_email", nullable = false, length = 150)
    private String requesterEmail;

    @Column(name = "requester_phone", length = 20)
    private String requesterPhone;

    @Column(name = "department_code", length = 50)
    private String departmentCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "office_location", nullable = false, length = 50)
    private OfficeLocation officeLocation;

    @Column(name = "asset_definition_id")
    private Long assetDefinitionId;

    @Column(name = "asset_category_id")
    private Long assetCategoryId;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    @Column(name = "procurement_required", nullable = false)
    @Builder.Default
    private Boolean procurementRequired = false;

    @Column(name = "approx_price", precision = 10, scale = 2)
    private BigDecimal approxPrice;

    @Column(name = "delivery_date")
    private LocalDate deliveryDate;

    @Column(length = 2000)
    private String justification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private AssetRequestStatus status = AssetRequestStatus.PENDING;

    @Column(name = "approved_by_user_id", length = 100)
    private String approvedByUserId;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
