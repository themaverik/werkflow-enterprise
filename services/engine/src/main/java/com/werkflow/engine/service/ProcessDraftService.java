package com.werkflow.engine.service;

import com.werkflow.engine.dto.ProcessDraftSummaryDTO;
import com.werkflow.engine.workflow.ProcessDraft;
import com.werkflow.engine.workflow.ProcessDraftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Collections;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProcessDraftService {

    private final ProcessDraftRepository repository;

    @Transactional
    public ProcessDraft saveDraft(String processKey, String name, String bpmnXml,
                                  String departmentCode, String categoryCode,
                                  List<String> tags, String userId, String tenantId) {
        log.info("Saving draft for process: {}", processKey);
        ProcessDraft draft = repository.findByProcessKeyAndTenantId(processKey, tenantId)
                .orElse(ProcessDraft.builder().processKey(processKey).tenantId(tenantId).createdBy(userId).build());
        draft.setName(name);
        draft.setBpmnXml(bpmnXml);
        if (departmentCode != null) draft.setDepartmentCode(departmentCode.isBlank() ? null : departmentCode);
        if (categoryCode != null) draft.setCategoryCode(categoryCode.isBlank() ? null : categoryCode);
        draft.setTags(tags != null ? tags : Collections.emptyList());
        draft.setUpdatedBy(userId);
        return repository.save(draft);
    }

    public Optional<ProcessDraft> getDraft(String processKey, String tenantId) {
        return repository.findByProcessKeyAndTenantId(processKey, tenantId);
    }

    @Transactional(readOnly = true)
    public List<ProcessDraftSummaryDTO> listDrafts(String tenantId) {
        return repository.findAllByTenantIdOrderByUpdatedAtDesc(tenantId).stream()
                .map(draft -> ProcessDraftSummaryDTO.builder()
                        .id(draft.getId())
                        .processKey(draft.getProcessKey())
                        .name(draft.getName())
                        .departmentCode(draft.getDepartmentCode())
                        .categoryCode(draft.getCategoryCode())
                        .tags(draft.getTags() != null ? draft.getTags() : List.of())
                        .createdBy(draft.getCreatedBy())
                        .updatedBy(draft.getUpdatedBy())
                        .createdAt(draft.getCreatedAt())
                        .updatedAt(draft.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteDraft(String processKey, String tenantId) {
        log.info("Deleting draft for process: {}", processKey);
        repository.deleteByProcessKeyAndTenantId(processKey, tenantId);
    }
}
