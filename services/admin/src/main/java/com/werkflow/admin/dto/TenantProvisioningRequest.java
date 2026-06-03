package com.werkflow.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantProvisioningRequest {

    @NotBlank(message = "Tenant code is required")
    @Size(max = 50, message = "Tenant code must not exceed 50 characters")
    private String tenantCode;

    @NotBlank(message = "Tenant name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @NotBlank(message = "Admin email is required")
    @Email(message = "Invalid admin email format")
    @Size(max = 100, message = "Admin email must not exceed 100 characters")
    private String adminEmail;

    @Size(max = 100, message = "Admin first name must not exceed 100 characters")
    private String adminFirstName;

    @Size(max = 100, message = "Admin last name must not exceed 100 characters")
    private String adminLastName;
}
