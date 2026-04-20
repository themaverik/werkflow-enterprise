package com.werkflow.business.inventory.dto;

import com.werkflow.business.inventory.entity.MaintenanceRecord.MaintenanceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceRecordResponse {

    private Long id;
    private Long assetInstanceId;
    private String assetTag;
    private MaintenanceType maintenanceType;
    private LocalDate scheduledDate;
    private LocalDate completedDate;
    private String performedBy;
    private BigDecimal cost;
    private String description;
    private LocalDate nextMaintenanceDate;
    private LocalDateTime createdAt;
}
