package com.werkflow.admin.service;

import com.werkflow.admin.dto.TenantProvisioningRequest;
import com.werkflow.admin.dto.TenantResponse;
import com.werkflow.admin.entity.Tenant;
import com.werkflow.admin.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Orchestrates tenant creation: DB row + Keycloak admin user provisioning.
 * Applies a compensating delete if the Keycloak call fails after the DB insert.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProvisioningService {

    private final TenantRepository tenantRepository;
    private final KeycloakUserService keycloakUserService;

    /**
     * Provisions a new tenant and its initial admin user.
     *
     * <ol>
     *   <li>Validates that the tenantCode is unique.</li>
     *   <li>Persists a new {@link Tenant} row (active=true).</li>
     *   <li>Creates the Keycloak admin user with the tenant_id attribute and invite actions.</li>
     *   <li>On Keycloak failure, deletes the saved row as a compensating action.</li>
     * </ol>
     *
     * @param request the provisioning request carrying tenantCode, name, and admin contact details
     * @return a {@link TenantResponse} reflecting the newly created tenant
     * @throws ResponseStatusException 409 if tenantCode already exists; 500 on Keycloak failure
     */
    public TenantResponse provision(TenantProvisioningRequest request) {
        if (tenantRepository.existsByTenantCode(request.getTenantCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tenant code already exists: " + request.getTenantCode());
        }

        Tenant tenant = new Tenant();
        tenant.setTenantCode(request.getTenantCode());
        tenant.setName(request.getName());
        tenant.setActive(true);
        Tenant saved = tenantRepository.save(tenant);

        try {
            keycloakUserService.createTenantAdminUser(
                    request.getAdminEmail(),
                    request.getAdminFirstName(),
                    request.getAdminLastName(),
                    request.getTenantCode()
            );
        } catch (Exception e) {
            log.error("Keycloak user creation failed for tenant={}, performing compensating delete: {}",
                    request.getTenantCode(), e.getMessage());
            tenantRepository.delete(saved);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Tenant provisioning failed: Keycloak user could not be created");
        }

        log.info("Tenant provisioned: tenantCode={}, adminEmail={}", saved.getTenantCode(), request.getAdminEmail());
        return TenantResponse.from(saved);
    }
}
