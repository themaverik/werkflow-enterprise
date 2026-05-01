package com.werkflow.engine.service;

import com.werkflow.engine.dto.DeadLetterJobResponse;
import org.flowable.engine.ManagementService;
import org.flowable.job.api.DeadLetterJobQuery;
import org.flowable.job.api.Job;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobManagementServiceTest {

    @Mock private ManagementService managementService;
    @Mock private DeadLetterJobQuery jobQuery;
    @InjectMocks private JobManagementService service;

    @Test
    void listDeadLetterJobs_returnsAllJobs_whenNoTenantFilter() {
        Job mockJob = mockJob("job-1", "tenant-a");
        when(managementService.createDeadLetterJobQuery()).thenReturn(jobQuery);
        when(jobQuery.orderByJobDuedate()).thenReturn(jobQuery);
        when(jobQuery.asc()).thenReturn(jobQuery);
        when(jobQuery.list()).thenReturn(List.of(mockJob));

        List<DeadLetterJobResponse> result = service.listDeadLetterJobs(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getJobId()).isEqualTo("job-1");
        assertThat(result.get(0).getTenantId()).isEqualTo("tenant-a");
    }

    @Test
    void listDeadLetterJobs_filtersbyTenantId() {
        when(managementService.createDeadLetterJobQuery()).thenReturn(jobQuery);
        when(jobQuery.jobTenantId("tenant-b")).thenReturn(jobQuery);
        when(jobQuery.orderByJobDuedate()).thenReturn(jobQuery);
        when(jobQuery.asc()).thenReturn(jobQuery);
        when(jobQuery.list()).thenReturn(List.of());

        List<DeadLetterJobResponse> result = service.listDeadLetterJobs("tenant-b");

        assertThat(result).isEmpty();
        verify(jobQuery).jobTenantId("tenant-b");
    }

    @Test
    void retryJob_callsMoveDeadLetterJob_withDefaultRetries() {
        service.retryJob("job-99");
        verify(managementService).moveDeadLetterJobToExecutableJob("job-99", 3);
    }

    @Test
    void retryJob_callsMoveDeadLetterJob_withCustomRetries() {
        service.retryJob("job-42", 5);
        verify(managementService).moveDeadLetterJobToExecutableJob("job-42", 5);
    }

    @Test
    void deleteJob_callsDeleteDeadLetterJob() {
        service.deleteJob("job-del");
        verify(managementService).deleteDeadLetterJob("job-del");
    }

    private Job mockJob(String id, String tenantId) {
        Job job = mock(Job.class);
        when(job.getId()).thenReturn(id);
        when(job.getTenantId()).thenReturn(tenantId);
        when(job.getJobType()).thenReturn("asyncContinuation");
        when(job.getProcessInstanceId()).thenReturn("proc-1");
        when(job.getProcessDefinitionId()).thenReturn("approval:1:abc");
        when(job.getExecutionId()).thenReturn("exec-1");
        when(job.getExceptionMessage()).thenReturn("Connection refused");
        when(job.getRetries()).thenReturn(0);
        when(job.getCreateTime()).thenReturn(new Date());
        when(job.getDuedate()).thenReturn(null);
        return job;
    }
}
