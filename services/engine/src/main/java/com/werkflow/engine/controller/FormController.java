package com.werkflow.engine.controller;

import com.werkflow.engine.dto.FormDefinitionResponse;
import com.werkflow.engine.service.FormService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing form definitions
 */
@Slf4j
@RestController
@RequestMapping("/werkflow/api/forms")
@RequiredArgsConstructor
@Tag(name = "Forms", description = "Form definition management")
@SecurityRequirement(name = "bearer-jwt")
public class FormController {

    private final FormService formService;

    /**
     * Get form definition by key
     */
    @GetMapping("/{formKey}")
    @Operation(summary = "Get form definition by key", description = "Retrieve form definition schema for a specific form key")
    public ResponseEntity<FormDefinitionResponse> getFormByKey(
        @Parameter(description = "Form key identifier") @PathVariable String formKey
    ) {
        log.info("REQUEST: FormController.getFormByKey() called with formKey={}", formKey);
        try {
            FormDefinitionResponse response = formService.getFormByKey(formKey);
            log.info("SUCCESS: Form definition retrieved for formKey={}", formKey);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("ERROR: Failed to retrieve form definition for formKey={}", formKey, e);
            throw e;
        }
    }
}
