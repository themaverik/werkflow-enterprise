package com.werkflow.engine.service;

import com.werkflow.engine.dto.ProcessStatsDTO;
import com.werkflow.engine.dto.TaskMetricsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Analytics queries against Flowable history tables.
 * All queries are index-backed via V4 migration (ACT_HI_PROCINST, ACT_HI_TASKINST).
 * M6 Group A — backend analytics before UI overhaul.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final HistoryService historyService;
    private final RuntimeService runtimeService;

    /**
     * Process execution stats for a tenant.
     * Uses indexed queries on ACT_HI_PROCINST: tenant_id_, end_time_, delete_reason_.
     */
    public ProcessStatsDTO getProcessStats(String tenantId) {
        log.debug("AnalyticsService.getProcessStats tenantId={}", tenantId);

        long running = queryCount(tenantId, false, false);
        long completed = queryCount(tenantId, true, false);
        long failed = queryCount(tenantId, true, true);
        long suspended = countSuspended(tenantId);
        long total = running + completed + failed + suspended;

        double avgDuration = computeAvgDuration(tenantId);
        double successRate = (completed + failed) > 0
                ? (double) completed / (completed + failed) * 100.0
                : 100.0;

        return ProcessStatsDTO.builder()
                .total(total)
                .running(running)
                .completed(completed)
                .failed(failed)
                .suspended(suspended)
                .avgDurationMinutes(avgDuration)
                .successRate(Math.round(successRate * 10.0) / 10.0)
                .tenantId(tenantId)
                .build();
    }

    /**
     * Task metrics for a tenant — cycle time, bottleneck, SLA compliance.
     * Uses indexed queries on ACT_HI_TASKINST: tenant_id_, task_def_key_, end_time_, due_date_.
     */
    public TaskMetricsDTO getTaskMetrics(String tenantId) {
        log.debug("AnalyticsService.getTaskMetrics tenantId={}", tenantId);

        List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                .taskTenantId(tenantId)
                .finished()
                .list();

        if (tasks.isEmpty()) {
            return TaskMetricsDTO.builder()
                    .avgCycleTimeMinutes(0)
                    .bottleneckTaskKey(null)
                    .bottleneckAvgMinutes(0)
                    .slaCompliancePct(100.0)
                    .totalTasksSampled(0)
                    .stepBreakdown(Collections.emptyList())
                    .tenantId(tenantId)
                    .build();
        }

        // Compute per-task-definition averages
        Map<String, List<Long>> durationsByKey = new HashMap<>();
        long slaCompliant = 0;
        long slaTotal = 0;

        for (HistoricTaskInstance t : tasks) {
            if (t.getCreateTime() == null || t.getEndTime() == null) continue;
            long durationMs = t.getEndTime().getTime() - t.getCreateTime().getTime();
            String key = t.getTaskDefinitionKey() != null ? t.getTaskDefinitionKey() : "unknown";
            durationsByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(durationMs);

            if (t.getDueDate() != null) {
                slaTotal++;
                if (!t.getEndTime().after(t.getDueDate())) slaCompliant++;
            }
        }

        List<TaskMetricsDTO.TaskStepMetric> breakdown = durationsByKey.entrySet().stream()
                .map(e -> {
                    double avgMs = e.getValue().stream().mapToLong(Long::longValue).average().orElse(0);
                    return TaskMetricsDTO.TaskStepMetric.builder()
                            .taskDefinitionKey(e.getKey())
                            .count(e.getValue().size())
                            .avgMinutes(Math.round(avgMs / 60000.0 * 10) / 10.0)
                            .build();
                })
                .sorted(Comparator.comparingDouble(TaskMetricsDTO.TaskStepMetric::getAvgMinutes).reversed())
                .collect(Collectors.toList());

        TaskMetricsDTO.TaskStepMetric bottleneck = breakdown.isEmpty() ? null : breakdown.get(0);

        double overallAvgMs = durationsByKey.values().stream()
                .flatMap(Collection::stream)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);

        double slaCompliancePct = slaTotal > 0
                ? Math.round((double) slaCompliant / slaTotal * 1000.0) / 10.0
                : 100.0;

        return TaskMetricsDTO.builder()
                .avgCycleTimeMinutes(Math.round(overallAvgMs / 60000.0 * 10) / 10.0)
                .bottleneckTaskKey(bottleneck != null ? bottleneck.getTaskDefinitionKey() : null)
                .bottleneckAvgMinutes(bottleneck != null ? bottleneck.getAvgMinutes() : 0)
                .slaCompliancePct(slaCompliancePct)
                .totalTasksSampled(tasks.size())
                .stepBreakdown(breakdown)
                .tenantId(tenantId)
                .build();
    }

    private long queryCount(String tenantId, boolean finished, boolean terminated) {
        var query = historyService.createHistoricProcessInstanceQuery()
                .processInstanceTenantId(tenantId);
        if (finished && terminated) {
            query = query.deleted();
        } else if (finished) {
            query = query.finished();
        } else {
            query = query.unfinished();
        }
        return query.count();
    }

    private long countSuspended(String tenantId) {
        try {
            return runtimeService.createProcessInstanceQuery()
                    .processInstanceTenantId(tenantId)
                    .suspended()
                    .count();
        } catch (Exception e) {
            log.warn("AnalyticsService: could not count suspended processes — {}", e.getMessage());
            return 0;
        }
    }

    private double computeAvgDuration(String tenantId) {
        List<HistoricProcessInstance> finished = historyService.createHistoricProcessInstanceQuery()
                .processInstanceTenantId(tenantId)
                .finished()
                .list();

        if (finished.isEmpty()) return 0;

        double avgMs = finished.stream()
                .filter(p -> p.getStartTime() != null && p.getEndTime() != null)
                .mapToLong(p -> p.getEndTime().getTime() - p.getStartTime().getTime())
                .average()
                .orElse(0);

        return Math.round(avgMs / 60000.0 * 10) / 10.0;
    }
}
