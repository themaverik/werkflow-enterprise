package com.werkflow.engine.workflow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Provides tenant-specific DOA amount thresholds from the doa_threshold table.
 * Replaces hardcoded threshold logic previously scattered across WorkflowAuthorizationService
 * and WorkflowTaskRouter.
 *
 * Results are cached per (tenantId, doaLevel) pair. Cache is invalidated by restart only —
 * thresholds are not expected to change frequently.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DoaThresholdService {

    private static final String DEFAULT_TENANT = "default";

    private final DoaThresholdRepository repository;

    /**
     * Returns the maximum approvable amount for a DOA level in a given tenant.
     * Returns empty Optional if no threshold is configured (treat as unlimited).
     */
    @Cacheable(value = "doaThresholds", key = "#tenantId + ':' + #doaLevel")
    public Optional<BigDecimal> getMaxAmount(String tenantId, String doaLevel) {
        return repository.findByTenantIdAndDoaLevel(tenantId, doaLevel)
            .map(DoaThreshold::getMaxAmount);
    }

    /**
     * Returns all thresholds for a tenant, ordered by DOA level.
     */
    @Cacheable(value = "doaThresholds", key = "#tenantId + ':all'")
    public List<DoaThreshold> getThresholds(String tenantId) {
        return repository.findByTenantIdOrderByDoaLevel(tenantId);
    }

    /**
     * Resolves the minimum DOA level required to approve a given amount for a tenant.
     * Returns the first DOA level whose max_amount >= amount, or DOA_L4 if none match.
     */
    @Cacheable(value = "doaThresholds", key = "#tenantId + ':required:' + #amount")
    public String resolveRequiredLevel(String tenantId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return FlowableGroups.DOA_L1;
        }

        // Call repository directly to avoid AOP proxy bypass (this.getThresholds bypasses @Cacheable)
        List<DoaThreshold> thresholds = repository.findByTenantIdOrderByDoaLevel(tenantId);
        for (DoaThreshold threshold : thresholds) {
            if (threshold.getMaxAmount() == null) {
                return threshold.getDoaLevel(); // unlimited
            }
            if (amount.compareTo(threshold.getMaxAmount()) <= 0) {
                return threshold.getDoaLevel();
            }
        }

        // No threshold covers this amount — require highest level
        log.warn("No DOA threshold covers amount {} for tenant {}, defaulting to DOA_L4",
            amount, tenantId);
        return FlowableGroups.DOA_L4;
    }

    /**
     * Updates label, description, maxAmount and currency for an existing threshold.
     * Evicts all cached entries for the threshold's tenant so routing picks up the change.
     */
    @Caching(evict = {
        @CacheEvict(value = "doaThresholds", key = "#result.tenantId + ':' + #result.doaLevel"),
        @CacheEvict(value = "doaThresholds", key = "#result.tenantId + ':all'"),
    })
    public DoaThreshold updateThreshold(Long id, DoaThreshold patch) {
        DoaThreshold existing = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "DoA threshold " + id + " not found"));
        existing.setLabel(patch.getLabel());
        existing.setDescription(patch.getDescription());
        existing.setMaxAmount(patch.getMaxAmount());
        existing.setCurrency(patch.getCurrency());
        return repository.save(existing);
    }

    /**
     * Resolves required DOA level using the default tenant configuration.
     */
    public String resolveRequiredLevel(BigDecimal amount) {
        return resolveRequiredLevel(DEFAULT_TENANT, amount);
    }
}
