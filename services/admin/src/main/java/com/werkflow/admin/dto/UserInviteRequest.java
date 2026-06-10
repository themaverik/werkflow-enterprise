package com.werkflow.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInviteRequest {

    @NotBlank @Email @Size(max = 100)
    private String email;

    @NotBlank @Size(max = 100)
    private String firstName;

    @NotBlank @Size(max = 100)
    private String lastName;

    // Role name as a string (e.g. "ADMIN", "EMPLOYEE") — resolved against roles table
    @NotBlank @Size(max = 50)
    private String roleName;

    @NotNull(message = "DOA level is required")
    @Min(value = 1, message = "DOA level must be at least 1")
    @Max(value = 4, message = "DOA level must not exceed 4")
    private Integer doaLevel;

    @Size(max = 50)
    private String departmentCode;
}
