package com.werkflow.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String keycloakId;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private String mobile;
    private Long organizationId;
    private String organizationName;
    private String jobTitle;
    private String employeeId;
    private Long managerId;
    private String managerName;
    private LocalDate hireDate;
    private String address;
    private String city;
    private String state;
    private String country;
    private String postalCode;
    private List<RoleResponse> roles;
    private Boolean active;
    private Boolean emailVerified;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String tenantCode;
    private Integer doaLevel;
}
