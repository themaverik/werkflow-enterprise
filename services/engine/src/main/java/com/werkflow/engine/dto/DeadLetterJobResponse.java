package com.werkflow.engine.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class DeadLetterJobResponse {
    private String jobId;
    private String jobType;
    private String processInstanceId;
    private String processDefinitionKey;
    private String executionId;
    private String tenantId;
    private String exceptionMessage;
    private OffsetDateTime createTime;
    private OffsetDateTime dueDate;
    private Integer retries;
}
