package com.werkflow.business.inventory.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for MaintenanceRecord creation and update
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceRecordRequestDto {

    @NotNull(message = "Asset instance ID is required")
    private Long assetInstanceId;

    @NotBlank(message = "Maintenance type is required")
    private String maintenanceType;

    private LocalDate scheduledDate;

    private LocalDate completedDate;

    @Size(max = 200, message = "Performed by cannot exceed 200 characters")
    private String performedBy;

    @DecimalMin(value = "0.00", message = "Cost cannot be negative")
    private BigDecimal cost;

    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;

    private LocalDate nextMaintenanceDate;
}
