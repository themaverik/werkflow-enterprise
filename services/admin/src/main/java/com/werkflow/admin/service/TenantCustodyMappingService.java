package com.werkflow.admin.service;

import com.werkflow.admin.dto.CustodyMappingRequest;
import com.werkflow.admin.dto.CustodyMappingResponse;
import com.werkflow.admin.entity.TenantCustodyMapping;
import com.werkflow.admin.repository.TenantCustodyMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenantCustodyMappingService {

    private final TenantCustodyMappingRepository repository;

    @Transactional(readOnly = true)
    public List<CustodyMappingResponse> listByTenant(String tenantCode) {
        return repository.findByTenantCodeOrderByCategoryKey(tenantCode)
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public CustodyMappingResponse create(CustodyMappingRequest request) {
        TenantCustodyMapping mapping = new TenantCustodyMapping();
        mapping.setTenantCode(request.tenantCode());
        mapping.setCategoryKey(request.categoryKey());
        mapping.setCustodyGroup(request.custodyGroup());
        mapping.setDisplayName(request.displayName());
        return toResponse(repository.save(mapping));
    }

    @Transactional
    public CustodyMappingResponse update(Long id, CustodyMappingRequest request) {
        TenantCustodyMapping mapping = repository.findById(id)
            .orElseThrow(() -> new RuntimeException("Custody mapping not found: " + id));
        mapping.setTenantCode(request.tenantCode());
        mapping.setCategoryKey(request.categoryKey());
        mapping.setCustodyGroup(request.custodyGroup());
        mapping.setDisplayName(request.displayName());
        return toResponse(repository.save(mapping));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new RuntimeException("Custody mapping not found: " + id);
        }
        repository.deleteById(id);
    }

    private CustodyMappingResponse toResponse(TenantCustodyMapping mapping) {
        return new CustodyMappingResponse(
            mapping.getId(),
            mapping.getTenantCode(),
            mapping.getCategoryKey(),
            mapping.getCustodyGroup(),
            mapping.getDisplayName(),
            mapping.getCreatedAt(),
            mapping.getUpdatedAt()
        );
    }
}
