package com.werkflow.engine.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.werkflow.engine.dto.*;
import com.werkflow.engine.exception.FormNotFoundException;
import com.werkflow.engine.exception.FormSubmissionException;
import com.werkflow.engine.exception.TaskNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing task forms and form submissions.
 * Integrates form-js schemas with Flowable tasks.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TaskFormService {

    private final TaskService flowableTaskService;
    private final RuntimeService runtimeService;
    private final FormSchemaService formSchemaService;
    private final FormSchemaValidator formSchemaValidator;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Get task form schema and variables
     * @param taskId The task ID
     * @return TaskFormResponse with schema and initial values
     * @throws TaskNotFoundException if task not found
     * @throws FormNotFoundException if form schema not found
     */
    public TaskFormResponse getTaskForm(String taskId) {
        log.info("Getting form for task: {}", taskId);

        // Get task
        Task task = flowableTaskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();

        if (task == null) {
            throw new TaskNotFoundException(taskId);
        }

        // Get form key
        String formKey = task.getFormKey();
        if (formKey == null || formKey.isEmpty()) {
            throw new FormNotFoundException("Task " + taskId + " has no associated form");
        }

        // Load form schema
        FormSchema formSchema = formSchemaService.loadFormSchema(formKey);

        // Get task variables to populate form
        Map<String, Object> variables = flowableTaskService.getVariables(taskId);

        // Build response
        return TaskFormResponse.builder()
                .formKey(formSchema.getFormKey())
                .version(formSchema.getVersion())
                .schema(formSchema.getSchemaJson())
                .variables(variables)
                .taskId(taskId)
                .processInstanceId(task.getProcessInstanceId())
                .description(formSchema.getDescription())
                .formType(formSchema.getFormType().name())
                .build();
    }

    /**
     * Get task form variables only (without schema)
     * @param taskId The task ID
     * @return Map of variables
     */
    public Map<String, Object> getTaskFormVariables(String taskId) {
        log.info("Getting form variables for task: {}", taskId);

        // Verify task exists
        Task task = flowableTaskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();

        if (task == null) {
            throw new TaskNotFoundException(taskId);
        }

        return flowableTaskService.getVariables(taskId);
    }

    /**
     * Validate form submission without completing task
     * @param taskId The task ID
     * @param formData The form data to validate
     * @throws FormSubmissionException if validation fails
     */
    public void validateTaskFormSubmission(String taskId, Map<String, Object> formData) {
        log.info("Validating form submission for task: {}", taskId);

        // Get task
        Task task = flowableTaskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();

        if (task == null) {
            throw new TaskNotFoundException(taskId);
        }

        // Get form schema
        String formKey = task.getFormKey();
        if (formKey == null || formKey.isEmpty()) {
            throw new FormNotFoundException("Task " + taskId + " has no associated form");
        }

        FormSchema formSchema = formSchemaService.loadFormSchema(formKey);

        // Validate form data against schema
        formSchemaValidator.validateFormData(formSchema.getSchemaJson(), formData);

        log.info("Form data validation successful for task: {}", taskId);
    }

    /**
     * Submit task form and complete task
     * @param taskId The task ID
     * @param request Form submission request
     * @param submittedBy User submitting the form
     * @return FormSubmitResponse with task completion details
     */
    @Transactional
    public FormSubmitResponse submitTaskForm(String taskId, FormSubmitRequest request, String submittedBy) {
        log.info("Submitting form for task: {} by user: {}", taskId, submittedBy);

        // Get task
        Task task = flowableTaskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();

        if (task == null) {
            throw new TaskNotFoundException(taskId);
        }

        // Get form schema
        String formKey = task.getFormKey();
        if (formKey == null || formKey.isEmpty()) {
            throw new FormNotFoundException("Task " + taskId + " has no associated form");
        }

        FormSchema formSchema = formSchemaService.loadFormSchema(formKey);
        String processInstanceId = task.getProcessInstanceId();

        try {
            // Validate form data
            formSchemaValidator.validateFormData(formSchema.getSchemaJson(), request.getFormData());

            // Save form submission to database
            UUID submissionId = saveFormSubmission(
                    formSchema.getId(),
                    taskId,
                    processInstanceId,
                    request.getFormData(),
                    submittedBy
            );

            // Prepare variables to save to process
            Map<String, Object> variablesToSave = new HashMap<>();

            // Add form data to variables
            variablesToSave.putAll(request.getFormData());

            // Add any additional variables from request
            if (request.getVariables() != null) {
                variablesToSave.putAll(request.getVariables());
            }

            // Add outcome if provided (for exclusive gateways)
            if (request.getOutcome() != null && !request.getOutcome().isEmpty()) {
                variablesToSave.put("outcome", request.getOutcome());
            }

            // Complete task with variables
            flowableTaskService.complete(taskId, variablesToSave);

            log.info("Task {} completed successfully with form submission {}", taskId, submissionId);

            // Get next task if any
            List<Task> nextTasks = flowableTaskService.createTaskQuery()
                    .processInstanceId(processInstanceId)
                    .active()
                    .list();

            // Check if process ended
            boolean processEnded = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult() == null;

            // Build response
            FormSubmitResponse.FormSubmitResponseBuilder responseBuilder = FormSubmitResponse.builder()
                    .taskId(taskId)
                    .processInstanceId(processInstanceId)
                    .status("COMPLETED")
                    .processEnded(processEnded)
                    .submittedAt(Instant.now())
                    .submittedBy(submittedBy);

            // Add next task info if available
            if (!nextTasks.isEmpty()) {
                Task nextTask = nextTasks.get(0);
                responseBuilder.nextTaskId(nextTask.getId())
                        .nextTaskName(nextTask.getName());
            }

            return responseBuilder.build();

        } catch (Exception e) {
            log.error("Failed to submit form for task: {}", taskId, e);

            // Save failed submission
            try {
                saveFailedFormSubmission(
                        formSchema.getId(),
                        taskId,
                        processInstanceId,
                        request.getFormData(),
                        submittedBy,
                        e.getMessage()
                );
            } catch (Exception ex) {
                log.error("Failed to save failed submission record", ex);
            }

            throw new FormSubmissionException("Failed to submit form: " + e.getMessage(), e);
        }
    }

    /**
     * Save form submission to database
     */
    private UUID saveFormSubmission(UUID formSchemaId, String taskId, String processInstanceId,
                                     Map<String, Object> formData, String submittedBy) {
        UUID id = UUID.randomUUID();

        String sql = """
                INSERT INTO form_submissions (id, form_schema_id, task_id, process_instance_id,
                                               form_data, submitted_at, submitted_by, submission_status)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, 'COMPLETED')
                """;

        try {
            String formDataJson = objectMapper.writeValueAsString(formData);

            jdbcTemplate.update(sql,
                    id,
                    formSchemaId,
                    taskId,
                    processInstanceId,
                    formDataJson,
                    Timestamp.from(Instant.now()),
                    submittedBy
            );

            log.info("Saved form submission: {}", id);
            return id;

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize form data", e);
            throw new FormSubmissionException("Failed to save form submission", e);
        }
    }

    /**
     * Save failed form submission
     */
    private void saveFailedFormSubmission(UUID formSchemaId, String taskId, String processInstanceId,
                                           Map<String, Object> formData, String submittedBy,
                                           String errorMessage) {
        UUID id = UUID.randomUUID();

        String sql = """
                INSERT INTO form_submissions (id, form_schema_id, task_id, process_instance_id,
                                               form_data, submitted_at, submitted_by, submission_status,
                                               validation_errors)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, 'FAILED', ?::jsonb)
                """;

        try {
            String formDataJson = objectMapper.writeValueAsString(formData);
            String errorJson = objectMapper.writeValueAsString(Map.of("error", errorMessage));

            jdbcTemplate.update(sql,
                    id,
                    formSchemaId,
                    taskId,
                    processInstanceId,
                    formDataJson,
                    Timestamp.from(Instant.now()),
                    submittedBy,
                    errorJson
            );

        } catch (JsonProcessingException e) {
            log.error("Failed to save failed submission", e);
        }
    }
}
