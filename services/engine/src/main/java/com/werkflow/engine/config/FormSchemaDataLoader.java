package com.werkflow.engine.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.werkflow.engine.dto.FormSchema;
import com.werkflow.engine.service.FormSchemaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * Data loader to pre-populate form schemas on application startup.
 * Only runs when 'init-data' profile is active.
 */
@Component
@Profile("init-data")
@RequiredArgsConstructor
@Slf4j
public class FormSchemaDataLoader implements CommandLineRunner {

    private final FormSchemaService formSchemaService;
    private final ObjectMapper objectMapper;

    @Override
    public void run(String... args) throws Exception {
        log.info("Starting form schema data initialization...");

        try {
            // Load CapEx Request Form
            loadFormSchema(
                    "capex-request-form",
                    "forms/capex-request-form.json",
                    "Capital Expenditure Request Form - 22 fields for CapEx approval workflow",
                    FormSchema.FormType.TASK_FORM
            );

            // Load CapEx Approval Form
            loadFormSchema(
                    "capex-approval-form",
                    "forms/capex-approval-form.json",
                    "Capital Expenditure Approval Form - 25 fields for CapEx approval decision",
                    FormSchema.FormType.APPROVAL
            );

            // Load Leave Request Form
            loadFormSchema(
                    "leave-request-form",
                    "forms/leave-request-form.json",
                    "Leave Request Form - 28 fields for employee leave requests",
                    FormSchema.FormType.TASK_FORM
            );

            // Load Leave Approval Form
            loadFormSchema(
                    "leave-approval-form",
                    "forms/leave-approval-form.json",
                    "Leave Approval Form - 24 fields for leave request approval",
                    FormSchema.FormType.APPROVAL
            );

            // Load Purchase Requisition Form
            loadFormSchema(
                    "purchase-requisition-form",
                    "forms/purchase-requisition-form.json",
                    "Purchase Requisition Form - 30 fields for procurement requests",
                    FormSchema.FormType.TASK_FORM
            );

            // Load Asset Request Form
            loadFormSchema(
                    "asset-request-form",
                    "forms/asset-request-form.json",
                    "Asset Request Form — dynamic category and asset selects via dataSource pattern",
                    FormSchema.FormType.TASK_FORM
            );

            log.info("Form schema data initialization completed successfully");

        } catch (Exception e) {
            log.error("Failed to initialize form schema data", e);
            // Don't fail application startup, just log the error
        }
    }

    private void loadFormSchema(String formKey, String resourcePath, String description,
                                 FormSchema.FormType formType) {
        try {
            log.info("Loading form schema: {}", formKey);

            // Check if form already exists
            try {
                formSchemaService.loadFormSchema(formKey);
                log.info("Form schema '{}' already exists, skipping", formKey);
                return;
            } catch (Exception e) {
                // Form doesn't exist, continue with loading
            }

            // Load JSON schema from classpath
            ClassPathResource resource = new ClassPathResource(resourcePath);
            InputStream inputStream = resource.getInputStream();
            JsonNode schemaJson = objectMapper.readTree(inputStream);

            // Save form schema
            formSchemaService.saveFormSchema(
                    formKey,
                    schemaJson,
                    description,
                    formType,
                    "system"
            );

            log.info("Successfully loaded form schema: {}", formKey);

        } catch (Exception e) {
            log.error("Failed to load form schema: {}", formKey, e);
            throw new RuntimeException("Failed to load form schema: " + formKey, e);
        }
    }
}
