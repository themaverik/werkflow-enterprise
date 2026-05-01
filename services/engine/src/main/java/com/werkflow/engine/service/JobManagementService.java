package com.werkflow.engine.service;

import com.werkflow.engine.dto.DeadLetterJobResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.job.api.Job;
import org.flowable.engine.ManagementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for monitoring and managing Flowable dead-letter jobs (M2 Performance).
 * Dead-letter jobs are async jobs that have exhausted their retry count.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobManagementService {

    private static final int DEFAULT_RETRY_COUNT = 3;

    private final ManagementService managementService;

    /**
     * Lists all dead-letter jobs, optionally filtered by tenant.
     */
    public List<DeadLetterJobResponse> listDeadLetterJobs(String tenantId) {
        var query = managementService.createDeadLetterJobQuery();
        if (tenantId != null && !tenantId.isBlank()) {
            query = query.jobTenantId(tenantId);
        }
        return query.orderByJobDuedate().asc().list()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Moves a dead-letter job back to the executable job queue with {@code retries} attempts.
     * Flowable will pick it up on the next async executor cycle.
     */
    @Transactional
    public void retryJob(String jobId, int retries) {
        log.info("Retrying dead-letter job: {} with {} retries", jobId, retries);
        managementService.moveDeadLetterJobToExecutableJob(jobId, retries);
    }

    /**
     * Moves a dead-letter job back to the executable queue with the default retry count.
     */
    @Transactional
    public void retryJob(String jobId) {
        retryJob(jobId, DEFAULT_RETRY_COUNT);
    }

    /**
     * Permanently deletes a dead-letter job.
     */
    @Transactional
    public void deleteJob(String jobId) {
        log.warn("Permanently deleting dead-letter job: {}", jobId);
        managementService.deleteDeadLetterJob(jobId);
    }

    private DeadLetterJobResponse toResponse(Job job) {
        String procDefKey = null;
        if (job.getProcessDefinitionId() != null) {
            String[] parts = job.getProcessDefinitionId().split(":");
            procDefKey = parts.length > 0 ? parts[0] : job.getProcessDefinitionId();
        }
        return DeadLetterJobResponse.builder()
                .jobId(job.getId())
                .jobType(job.getJobType())
                .processInstanceId(job.getProcessInstanceId())
                .processDefinitionKey(procDefKey)
                .executionId(job.getExecutionId())
                .tenantId(job.getTenantId())
                .exceptionMessage(job.getExceptionMessage())
                .createTime(job.getCreateTime() != null
                        ? job.getCreateTime().toInstant().atOffset(ZoneOffset.UTC) : null)
                .dueDate(job.getDuedate() != null
                        ? job.getDuedate().toInstant().atOffset(ZoneOffset.UTC) : null)
                .retries(job.getRetries())
                .build();
    }
}
