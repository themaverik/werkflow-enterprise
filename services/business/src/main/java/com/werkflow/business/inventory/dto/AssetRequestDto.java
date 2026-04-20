package com.werkflow.business.inventory.dto;

import com.werkflow.business.hr.entity.OfficeLocation;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetRequestDto {

    @NotBlank private String requesterUserId;
    @NotBlank private String requesterName;
    @NotBlank @Email private String requesterEmail;
    private String requesterPhone;
    private String departmentCode;

    @NotNull private OfficeLocation officeLocation;

    private Long assetDefinitionId;
    private Long assetCategoryId;

    @NotNull @Min(1) private Integer quantity;

    @Builder.Default private Boolean procurementRequired = false;
    private BigDecimal approxPrice;

    @FutureOrPresent(message = "Delivery date must be today or in the future")
    private LocalDate deliveryDate;

    @Size(max = 2000) private String justification;
}
