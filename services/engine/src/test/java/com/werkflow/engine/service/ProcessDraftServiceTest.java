package com.werkflow.engine.service;

import com.werkflow.engine.dto.ProcessDraftSummaryDTO;
import com.werkflow.engine.workflow.ProcessDraft;
import com.werkflow.engine.workflow.ProcessDraftRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessDraftServiceTest {

    @Mock
    private ProcessDraftRepository repository;

    @InjectMocks
    private ProcessDraftService processDraftService;

    @Test
    void listDrafts_returnsSummariesOrderedByUpdatedAtDesc() {
        String tenantId = "tenant-1";
        Instant earlier = Instant.parse("2026-03-28T10:00:00Z");
        Instant later   = Instant.parse("2026-03-28T12:00:00Z");

        ProcessDraft draft1 = ProcessDraft.builder()
                .id(UUID.randomUUID())
                .processKey("process-a")
                .name("Process A")
                .bpmnXml("<xml/>")
                .tenantId(tenantId)
                .updatedAt(later)
                .build();
        ProcessDraft draft2 = ProcessDraft.builder()
                .id(UUID.randomUUID())
                .processKey("process-b")
                .name("Process B")
                .bpmnXml("<xml/>")
                .tenantId(tenantId)
                .updatedAt(earlier)
                .build();
        when(repository.findAllByTenantIdOrderByUpdatedAtDesc(tenantId)).thenReturn(List.of(draft1, draft2));

        List<ProcessDraftSummaryDTO> result = processDraftService.listDrafts(tenantId);

        assertEquals(2, result.size());
        assertEquals("process-a", result.get(0).getProcessKey());
        assertEquals("process-b", result.get(1).getProcessKey());
        verify(repository).findAllByTenantIdOrderByUpdatedAtDesc(tenantId);
    }

    @Test
    void listDrafts_returnsEmptyListWhenNoDrafts() {
        String tenantId = "tenant-1";
        when(repository.findAllByTenantIdOrderByUpdatedAtDesc(tenantId)).thenReturn(List.of());

        List<ProcessDraftSummaryDTO> result = processDraftService.listDrafts(tenantId);

        assertNotNull(result);
        assertEquals(0, result.size());
        verify(repository).findAllByTenantIdOrderByUpdatedAtDesc(tenantId);
    }
}
