package com.werkflow.business.procurement.dto;

import com.werkflow.business.procurement.entity.Vendor.VendorStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorRequest {
    @NotBlank(message = "Vendor name is required")
    @Size(max = 200)
    private String name;

    @Size(max = 100)
    private String contactPerson;

    @Email(message = "Invalid email format")
    @Size(max = 100)
    private String email;

    @Size(max = 20)
    private String phone;

    @Size(max = 500)
    private String address;

    @Size(max = 50)
    private String taxId;

    @Size(max = 100)
    private String paymentTerms;

    private VendorStatus status;

    @PositiveOrZero(message = "Rating must be zero or positive")
    private BigDecimal rating;

    @Size(max = 1000)
    private String notes;

    private Map<String, Object> metadata;
}
