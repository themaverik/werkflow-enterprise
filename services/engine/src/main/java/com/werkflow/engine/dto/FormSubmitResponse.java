package com.werkflow.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for form submission.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormSubmitResponse {

    /**
     * Task ID that was completed
     */
    private String taskId;

    /**
     * Process instance ID
     */
    private String processInstanceId;

    /**
     * Status of the submission
     */
    private String status;

    /**
     * Next task ID if available
     */
    private String nextTaskId;

    /**
     * Next task name if available
     */
    private String nextTaskName;

    /**
     * Whether the process has ended
     */
    private Boolean processEnded;

    /**
     * Timestamp when the submission was processed
     */
    private Instant submittedAt;

    /**
     * User who submitted the form
     */
    private String submittedBy;
}
