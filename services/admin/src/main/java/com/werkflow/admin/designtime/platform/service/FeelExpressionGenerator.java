package com.werkflow.admin.designtime.platform.service;

import com.werkflow.admin.config.CacheConfig;
import com.werkflow.admin.designtime.platform.client.ErpClient;
import com.werkflow.admin.designtime.platform.dto.FeelExpressionCatalog;
import com.werkflow.admin.entity.ConfigurationVariable;
import com.werkflow.admin.repository.ConfigurationVariableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Projects configVars (DOA thresholds, admin-service DB) and custodyVars (ERP)
 * into a FEEL expression catalog for DMN cell autocomplete (ADR-002, ADR-004).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeelExpressionGenerator {

    private final ConfigurationVariableRepository configRepo;
    private final RestTemplate restTemplate;

    @Value("${app.erp-service.url:http://werkflow-business:8084}")
    private String erpBaseUrl;

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    /**
     * Generates the full FEEL expression catalog for the tenant.
     */
    @Cacheable(value = CacheConfig.PSS_FEEL_EXPRESSIONS, key = "#tenantCode")
    @Transactional(readOnly = true)
    public FeelExpressionCatalog generate(String tenantCode) {
        List<ConfigurationVariable> doaVars = configRepo
                .findByTenantCodeAndVarTypeOrderByVarKey(tenantCode, "DOA_THRESHOLD");

        String currency = configRepo.findByTenantCodeAndVarKey(tenantCode, "currency")
                .map(ConfigurationVariable::getVarValue)
                .orElse("USD");

        List<FeelExpressionCatalog.MonetaryEntry> monetary = buildMonetaryEntries(doaVars, currency);
        List<FeelExpressionCatalog.GroupResolutionEntry> groupResolutions = fetchCustodyVarsFromErp(tenantCode);
        List<String> lookupExpressions = List.of("custodyVars[category]", "custodyVars[assetType]");

        return new FeelExpressionCatalog(
                new FeelExpressionCatalog.ConfigVarsSection(monetary),
                new FeelExpressionCatalog.CustodyVarsSection(groupResolutions, lookupExpressions)
        );
    }

    /** Fetches custody var group resolutions from ERP (ADR-004). Degrades gracefully on failure. */
    @SuppressWarnings("unchecked")
    private List<FeelExpressionCatalog.GroupResolutionEntry> fetchCustodyVarsFromErp(String tenantCode) {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(erpBaseUrl + "/api/v1/custody-mappings")
                    .queryParam("tenantId", tenantCode)
                    .toUriString();
            List<Map<String, Object>> body = restTemplate
                    .exchange(url, HttpMethod.GET, null, LIST_MAP_TYPE)
                    .getBody();
            if (body == null) return Collections.emptyList();
            return body.stream().map(m -> {
                String key = (String) m.getOrDefault("custodyOwner", "");
                List<String> groups = (List<String>) m.getOrDefault("candidateGroups", List.of());
                return new FeelExpressionCatalog.GroupResolutionEntry(
                        key, groups, "custodyVars." + key);
            }).collect(Collectors.toList());
        } catch (Exception e) {
            log.debug("FeelExpressionGenerator: custodyVars unavailable from ERP — {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<FeelExpressionCatalog.MonetaryEntry> buildMonetaryEntries(
            List<ConfigurationVariable> doaVars, String currency) {
        List<FeelExpressionCatalog.MonetaryEntry> entries = new ArrayList<>();
        String prevKey = null;
        for (ConfigurationVariable var : doaVars) {
            String key = var.getVarKey();
            String rawValue = var.getVarValue();
            String displayValue = "unlimited".equalsIgnoreCase(rawValue) ? "unlimited" : rawValue;
            List<String> expressions = buildMonetaryExpressions(key, prevKey, rawValue);
            entries.add(new FeelExpressionCatalog.MonetaryEntry(
                    key,
                    var.getDescription() != null ? var.getDescription() : key + " threshold",
                    displayValue,
                    currency,
                    expressions
            ));
            prevKey = key;
        }
        return entries;
    }

    private List<String> buildMonetaryExpressions(String key, String prevKey, String rawValue) {
        if ("unlimited".equalsIgnoreCase(rawValue)) {
            return List.of("configVars." + key);
        }
        List<String> exprs = new ArrayList<>(Arrays.asList(
                "configVars." + key,
                "<= configVars." + key,
                "< configVars." + key
        ));
        if (prevKey != null) {
            exprs.add("(configVars." + prevKey + "..configVars." + key + "]");
        }
        return exprs;
    }

    private List<FeelExpressionCatalog.GroupResolutionEntry> buildGroupResolutions(
            List<ConfigurationVariable> custodyVars) {
        Map<String, List<String>> keyToGroups = custodyVars.stream()
                .collect(Collectors.groupingBy(
                        ConfigurationVariable::getVarKey,
                        Collectors.mapping(cv -> cv.getVarValue().strip(), Collectors.toList())
                ));
        return keyToGroups.entrySet().stream()
                .map(e -> new FeelExpressionCatalog.GroupResolutionEntry(
                        e.getKey(),
                        e.getValue(),
                        "custodyVars." + e.getKey()
                ))
                .collect(Collectors.toList());
    }
}
