package com.werkflow.engine.service;

import com.werkflow.engine.dto.ProcessStatsDTO;
import com.werkflow.engine.dto.TaskMetricsDTO;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalyticsServiceTest {

    @Mock HistoryService historyService;
    @Mock RuntimeService runtimeService;
    @InjectMocks AnalyticsService analyticsService;

    @Mock HistoricProcessInstanceQuery procQuery;
    @Mock HistoricTaskInstanceQuery taskQuery;
    @Mock ProcessInstanceQuery runtimeQuery;

    @BeforeEach
    void setUp() {
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(procQuery);
        when(procQuery.processInstanceTenantId(anyString())).thenReturn(procQuery);
        when(procQuery.finished()).thenReturn(procQuery);
        when(procQuery.unfinished()).thenReturn(procQuery);
        when(procQuery.deleted()).thenReturn(procQuery);
    }

    @Test
    void getProcessStats_returnsCounts() {
        when(procQuery.count()).thenReturn(5L, 10L, 2L);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(runtimeQuery);
        when(runtimeQuery.processInstanceTenantId(anyString())).thenReturn(runtimeQuery);
        when(runtimeQuery.suspended()).thenReturn(runtimeQuery);
        when(runtimeQuery.count()).thenReturn(1L);
        when(procQuery.list()).thenReturn(List.of());

        ProcessStatsDTO stats = analyticsService.getProcessStats("acme");

        assertThat(stats.getTenantId()).isEqualTo("acme");
        assertThat(stats.getRunning()).isEqualTo(5L);
        assertThat(stats.getCompleted()).isEqualTo(10L);
        assertThat(stats.getFailed()).isEqualTo(2L);
        assertThat(stats.getSuspended()).isEqualTo(1L);
    }

    @Test
    void getProcessStats_successRateCalculation() {
        when(procQuery.count()).thenReturn(0L, 8L, 2L);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(runtimeQuery);
        when(runtimeQuery.processInstanceTenantId(anyString())).thenReturn(runtimeQuery);
        when(runtimeQuery.suspended()).thenReturn(runtimeQuery);
        when(runtimeQuery.count()).thenReturn(0L);
        when(procQuery.list()).thenReturn(List.of());

        ProcessStatsDTO stats = analyticsService.getProcessStats("acme");

        assertThat(stats.getSuccessRate()).isEqualTo(80.0);
    }

    @Test
    void getTaskMetrics_emptyReturnsDefaults() {
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskQuery);
        when(taskQuery.taskTenantId(anyString())).thenReturn(taskQuery);
        when(taskQuery.finished()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of());

        TaskMetricsDTO metrics = analyticsService.getTaskMetrics("acme");

        assertThat(metrics.getTotalTasksSampled()).isEqualTo(0);
        assertThat(metrics.getSlaCompliancePct()).isEqualTo(100.0);
        assertThat(metrics.getBottleneckTaskKey()).isNull();
    }

    @Test
    void getTaskMetrics_identifiesBottleneck() {
        long now = System.currentTimeMillis();
        HistoricTaskInstance fastTask = mockTask("approveTask", now - 30 * 60000L, now, null);
        HistoricTaskInstance slowTask = mockTask("reviewTask", now - 120 * 60000L, now, null);

        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskQuery);
        when(taskQuery.taskTenantId(anyString())).thenReturn(taskQuery);
        when(taskQuery.finished()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(fastTask, slowTask));

        TaskMetricsDTO metrics = analyticsService.getTaskMetrics("acme");

        assertThat(metrics.getBottleneckTaskKey()).isEqualTo("reviewTask");
        assertThat(metrics.getTotalTasksSampled()).isEqualTo(2);
    }

    private HistoricTaskInstance mockTask(String key, long start, long end, Date dueDate) {
        HistoricTaskInstance t = mock(HistoricTaskInstance.class);
        when(t.getTaskDefinitionKey()).thenReturn(key);
        when(t.getCreateTime()).thenReturn(new Date(start));
        when(t.getEndTime()).thenReturn(new Date(end));
        when(t.getDueDate()).thenReturn(dueDate);
        return t;
    }
}
