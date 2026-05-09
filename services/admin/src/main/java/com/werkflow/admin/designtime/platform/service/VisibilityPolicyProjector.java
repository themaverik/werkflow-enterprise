package com.werkflow.admin.designtime.platform.service;

import com.werkflow.admin.config.CacheConfig;
import com.werkflow.admin.designtime.platform.dto.VisibilityPolicyEntry;
import com.werkflow.admin.entity.RoleGroupMapping;
import com.werkflow.admin.repository.ConfigurationVariableRepository;
import com.werkflow.admin.repository.RoleGroupMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Projects the tenant visibility policy — reads the managerScope POLICY config var
 * and the list of manager-tier groups from role_group_mappings.
 */
@Service
@RequiredArgsConstructor
public class VisibilityPolicyProjector {

    private static final String MANAGER_SCOPE_KEY = "managerScope";
    private static final String DEFAULT_SCOPE = "OWN_DEPT";

    private final ConfigurationVariableRepository configRepo;
    private final RoleGroupMappingRepository roleGroupMappingRepository;

    /**
     * Returns the tenant's current visibility policy.
     */
    @Cacheable(value = CacheConfig.PSS_VISIBILITY_POLICY, key = "#tenantCode")
    @Transactional(readOnly = true)
    public VisibilityPolicyEntry project(String tenantCode) {
        String scope = configRepo
                .findByTenantCodeAndVarKey(tenantCode, MANAGER_SCOPE_KEY)
                .map(v -> v.getVarValue())
                .orElse(DEFAULT_SCOPE);

        List<String> managerTierGroups = roleGroupMappingRepository
                .findByTenantCodeOrderByRoleName(tenantCode)
                .stream()
                .filter(RoleGroupMapping::isManagerTier)
                .map(RoleGroupMapping::getGroupName)
                .distinct()
                .collect(Collectors.toList());

        return new VisibilityPolicyEntry(scope, managerTierGroups);
    }

    /** Evicts cache on policy change. */
    @CacheEvict(value = {CacheConfig.PSS_VISIBILITY_POLICY, CacheConfig.PSS_CAPABILITIES}, key = "#tenantCode")
    public void evict(String tenantCode) {}
}
