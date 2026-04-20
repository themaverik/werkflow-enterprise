package com.werkflow.business.inventory.dto;

import com.werkflow.business.inventory.entity.MaintenanceRecord.MaintenanceType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceRecordRequest {

    @NotNull(message = "Asset instance ID is required")
    private Long assetInstanceId;

    @NotNull(message = "Maintenance type is required")
    private MaintenanceType maintenanceType;

    private LocalDate scheduledDate;

    private LocalDate completedDate;

    @Size(max = 200, message = "Performed by must not exceed 200 characters")
    private String performedBy;

    @Positive(message = "Cost must be positive")
    private BigDecimal cost;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    private LocalDate nextMaintenanceDate;
}
