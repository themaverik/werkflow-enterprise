package com.werkflow.engine.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * DTO representing a form submission for a Flowable task.
 * Contains the submitted form data and metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskFormSubmission {

    /**
     * Unique identifier for the submission
     */
    private UUID id;

    /**
     * Reference to the form schema used
     */
    private UUID formSchemaId;

    /**
     * Flowable task ID
     */
    private String taskId;

    /**
     * Flowable process instance ID
     */
    private String processInstanceId;

    /**
     * Submitted form data as JSON
     */
    private JsonNode formData;

    /**
     * Submitted form data as Map for easier processing
     */
    private Map<String, Object> formDataMap;

    /**
     * Timestamp when the form was submitted
     */
    private Instant submittedAt;

    /**
     * User who submitted the form
     */
    private String submittedBy;

    /**
     * Any validation errors encountered
     */
    private JsonNode validationErrors;

    /**
     * Status of the submission: SUBMITTED, VALIDATED, COMPLETED, FAILED
     */
    private SubmissionStatus submissionStatus;

    /**
     * Enumeration for submission status
     */
    public enum SubmissionStatus {
        SUBMITTED,
        VALIDATED,
        COMPLETED,
        FAILED
    }
}
