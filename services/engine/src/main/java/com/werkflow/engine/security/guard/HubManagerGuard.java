package com.werkflow.engine.security.guard;

import com.werkflow.engine.security.KeycloakRoleExtractor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.List;

@Service
public class HubManagerGuard implements DomainGuard {

    @Override
    public String supports() {
        return "Hub";
    }


    private final KeycloakRoleExtractor roleExtractor;

    public HubManagerGuard(KeycloakRoleExtractor roleExtractor) {
        this.roleExtractor = roleExtractor;
    }

    public boolean canAct(Authentication auth, Serializable hubId, String action) {
        if (hubId == null) {
            return false;
        }

        Jwt jwt = (Jwt) auth.getPrincipal();
        List<String> roles = roleExtractor.extractRoleNames(jwt);

        if (roles.contains("CENTRAL_HUB_MANAGER")) {
            return true;
        }

        String userHubId = roleExtractor.getHubId(jwt);
        return userHubId != null && userHubId.equals(hubId.toString());
    }
}
