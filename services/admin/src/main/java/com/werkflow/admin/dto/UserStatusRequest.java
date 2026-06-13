package com.werkflow.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserStatusRequest {

    @NotNull
    private Boolean active;
}
