package com.werkflow.engine.security.guard;

import com.werkflow.engine.security.KeycloakRoleExtractor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.Serializable;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "werkflow.business.enabled", havingValue = "true", matchIfMissing = false)
public class AssetRequestGuard implements DomainGuard {

    @Override
    public String supports() {
        return "AssetRequest";
    }


    private final KeycloakRoleExtractor roleExtractor;
    private final RestTemplate restTemplate;
    private final String businessServiceUrl;

    public AssetRequestGuard(KeycloakRoleExtractor roleExtractor,
                              RestTemplate restTemplate,
                              @Value("${business.service.url:http://localhost:8084}") String businessServiceUrl) {
        this.roleExtractor = roleExtractor;
        this.restTemplate = restTemplate;
        this.businessServiceUrl = businessServiceUrl;
    }

    public boolean canAct(Authentication auth, Serializable requestId, String action) {
        return switch (action) {
            case "APPROVE" -> checkApprovalEligibility(auth, requestId.toString());
            default        -> true;
        };
    }

    @SuppressWarnings("unchecked")
    private boolean checkApprovalEligibility(Authentication auth, String requestId) {
        Jwt jwt = (Jwt) auth.getPrincipal();
        String currentUserId = roleExtractor.getUserId(jwt);
        Integer userDoaLevel = roleExtractor.getDoaLevel(jwt);

        if (currentUserId == null || userDoaLevel == null) {
            return false;
        }

        Map<String, Object> context;
        try {
            context = restTemplate.getForObject(
                businessServiceUrl + "/api/inventory/asset-requests/" + requestId + "/approval-context",
                Map.class
            );
        } catch (org.springframework.web.client.RestClientException e) {
            return false;
        }

        if (context == null) {
            return false;
        }

        String submitterManagerId = (String) context.get("submitterManagerId");
        Number requiredDoaLevel = (Number) context.get("requiredDoaLevel");

        return currentUserId.equals(submitterManagerId)
            && requiredDoaLevel != null
            && userDoaLevel >= requiredDoaLevel.intValue();
    }
}
