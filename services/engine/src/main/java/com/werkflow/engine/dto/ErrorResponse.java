package com.werkflow.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Standard error response DTO for API exceptions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    private String error;
    private String message;
    private String path;
    private Integer status;

    @Builder.Default
    private OffsetDateTime timestamp = OffsetDateTime.now();

    private Map<String, String> validationErrors;
}
