package com.werkflow.engine.dto;

import java.util.List;

/**
 * Response body for HTTP 422 when a deploy references forms or decisions that
 * do not exist for the caller's tenant ({@link com.werkflow.engine.exception.DanglingReferenceException}).
 */
public record DanglingReferenceResponse(
        String message,
        List<String> missingForms,
        List<String> missingDecisions) {
}
