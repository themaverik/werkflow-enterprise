package com.werkflow.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TenantUpdateRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    private boolean active;
}
