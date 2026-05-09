package com.werkflow.admin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "role_group_mappings")
@Getter @Setter @NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class RoleGroupMapping {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String tenantCode;

    @Column(nullable = false, length = 100)
    private String roleName;

    @Column(nullable = false, length = 200)
    private String groupName;

    /** ADR-010: true when this group counts for cross-department manager visibility. */
    @Column(nullable = false)
    private boolean isManagerTier = false;

    @CreatedDate @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
