package com.werkflow.engine.dmn;

import com.werkflow.engine.client.AdminServiceClient;
import com.werkflow.engine.client.ErpServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Enriches DMN input variable maps with tenant-scoped context from admin and ERP services.
 *
 * Injects two FEEL context variables per evaluation:
 *   configVars  — Map<String, String>  from admin config vars (ADR-002)
 *   custodyVars — Map<String, List<String>> from ERP custody mappings (ADR-004)
 *
 * Both are cached for 5 minutes per tenant to avoid hot-path HTTP calls.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DmnConfigVariableInjector {

    private final AdminServiceClient adminServiceClient;
    private final ErpServiceClient erpServiceClient;

    /**
     * Returns a new map containing all entries from {@code inputVariables} plus
     * {@code configVars} and {@code custodyVars} populated from remote services.
     */
    public Map<String, Object> enrich(String tenantId, Map<String, Object> inputVariables) {
        Map<String, Object> enriched = new java.util.HashMap<>(inputVariables);

        Map<String, String> configVars = adminServiceClient.getConfigVars(tenantId);
        enriched.put("configVars", configVars);
        log.debug("DmnConfigVariableInjector: injected {} configVars for tenant {}", configVars.size(), tenantId);

        Map<String, List<String>> custodyVars = erpServiceClient.getCustodyMappings(tenantId);
        enriched.put("custodyVars", custodyVars);
        log.debug("DmnConfigVariableInjector: injected {} custodyVars for tenant {}", custodyVars.size(), tenantId);

        return enriched;
    }
}
