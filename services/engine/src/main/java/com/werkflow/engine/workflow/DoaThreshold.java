package com.werkflow.engine.workflow;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Tenant-specific DOA amount threshold.
 * max_amount = null means unlimited authority (DOA_L4 pattern).
 */
@Entity
@Table(
    name = "doa_threshold",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_doa_threshold_tenant_level",
        columnNames = {"tenant_id", "doa_level"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class DoaThreshold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "doa_level", nullable = false, length = 20)
    private String doaLevel;

    @Column(name = "max_amount", precision = 15, scale = 2)
    private BigDecimal maxAmount; // null = unlimited

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "label", length = 100)
    private String label;

    @Column(name = "description", length = 500)
    private String description;
}
