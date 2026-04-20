package com.werkflow.business.inventory.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "transfer_requests", schema = "inventory_service")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class TransferRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_instance_id", nullable = false)
    private AssetInstance assetInstance;

    // Foreign keys to admin_service.departments
    @Column(name = "from_dept_id", nullable = false)
    private Long fromDeptId;

    // Foreign keys to admin_service.users
    @Column(name = "from_user_id")
    private Long fromUserId;

    @Column(name = "to_dept_id", nullable = false)
    private Long toDeptId;

    @Column(name = "to_user_id")
    private Long toUserId;

    @Enumerated(EnumType.STRING)
    @Column(length = 50, nullable = false)
    private TransferType transferType;

    @Column(nullable = false, length = 1000)
    private String transferReason;

    @Column
    private LocalDate expectedReturnDate; // for loans

    @Column(name = "initiated_by_user_id", nullable = false)
    private Long initiatedByUserId;

    @Column(nullable = false)
    private LocalDateTime initiatedDate;

    @Column(name = "approved_by_user_id")
    private Long approvedByUserId;

    @Column
    private LocalDateTime approvedDate;

    @Column
    private LocalDateTime completedDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 50, nullable = false)
    @Builder.Default
    private TransferStatus status = TransferStatus.PENDING;

    @Column(length = 255)
    private String processInstanceId; // Flowable workflow instance

    @Column(length = 1000)
    private String rejectionReason;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum TransferType {
        INTER_DEPARTMENT,
        RETURN_TO_OWNER,
        DISPOSAL,
        LOAN
    }

    public enum TransferStatus {
        PENDING,
        APPROVED,
        REJECTED,
        COMPLETED,
        CANCELLED
    }
}
