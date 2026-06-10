package com.werkflow.admin.service;

import com.werkflow.admin.dto.TenantProvisioningRequest;
import com.werkflow.admin.dto.TenantResponse;
import com.werkflow.admin.entity.Organization;
import com.werkflow.admin.entity.Role;
import com.werkflow.admin.entity.Tenant;
import com.werkflow.admin.entity.User;
import com.werkflow.admin.repository.OrganizationRepository;
import com.werkflow.admin.repository.RoleRepository;
import com.werkflow.admin.repository.TenantRepository;
import com.werkflow.admin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Orchestrates tenant creation: DB rows (Tenant + Organization + admin User) + Keycloak user.
 *
 * <p>DB writes are wrapped in {@code @Transactional}. The Keycloak call is non-transactional by
 * nature. On KC failure, compensating deletes remove the three DB rows.
 *
 * <p>Contract: KC username = adminEmail = admin DB {@code keycloak_id} = admin DB {@code username}.
 * This matches how {@code JwtUserContext.userId = preferred_username = email} is used as the lookup
 * key in {@code getUserProfile}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProvisioningService {

    private final TenantRepository tenantRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final KeycloakUserService keycloakUserService;

    @Value("${app.keycloak.realm:werkflow}")
    private String keycloakRealm;

    /**
     * Provisions a new tenant: Tenant + Organization + admin User DB rows, then Keycloak user.
     *
     * <ol>
     *   <li>Validates that the tenantCode is unique.</li>
     *   <li>Persists Tenant, Organization, and admin User rows (atomic via {@code @Transactional}).</li>
     *   <li>Creates the Keycloak admin user with invite actions.</li>
     *   <li>On Keycloak failure, runs compensating deletes for all three DB rows.</li>
     * </ol>
     *
     * @param request provisioning request — tenantCode, name, and admin contact details
     * @return {@link TenantResponse} for the newly created tenant
     * @throws ResponseStatusException 409 if tenantCode already exists; 500 on Keycloak failure
     */
    @Transactional
    public TenantResponse provision(TenantProvisioningRequest request) {
        if (tenantRepository.existsByTenantCode(request.getTenantCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tenant code already exists: " + request.getTenantCode());
        }

        // 1. Persist Tenant row
        Tenant tenant = new Tenant();
        tenant.setTenantCode(request.getTenantCode());
        tenant.setName(request.getName());
        tenant.setActive(true);
        Tenant saved = tenantRepository.save(tenant);

        // 2. Create Organization row for this tenant (one org per tenant)
        Organization org = Organization.builder()
                .name(request.getName())
                .tenantCode(request.getTenantCode())   // MUST set — DDL is NOT NULL
                .active(true)
                .build();
        Organization savedOrg = organizationRepository.save(org);

        // 3. Create admin User row — keycloakId and username = email (preferred_username contract)
        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "ADMIN role not found in admin DB — check V1 seed migration"));

        User adminUser = User.builder()
                .keycloakId(request.getAdminEmail())
                .username(request.getAdminEmail())
                .email(request.getAdminEmail())
                .firstName(request.getAdminFirstName() != null ? request.getAdminFirstName() : "")
                .lastName(request.getAdminLastName() != null ? request.getAdminLastName() : "")
                .organization(savedOrg)
                .tenantCode(request.getTenantCode())
                .doaLevel(3)
                .active(true)
                .emailVerified(false)
                .roles(List.of(adminRole))
                .build();
        userRepository.save(adminUser);

        // 4. Create KC user — non-transactional; compensate with DB deletes on failure
        try {
            keycloakUserService.createKeycloakUser(
                    request.getAdminEmail(),
                    request.getAdminFirstName(),
                    request.getAdminLastName(),
                    request.getTenantCode(),
                    "admin"
            );
        } catch (Exception e) {
            log.error("Keycloak user creation failed for tenant={}, performing compensating deletes: {}",
                    request.getTenantCode(), e.getMessage());
            userRepository.delete(adminUser);
            organizationRepository.delete(savedOrg);
            tenantRepository.delete(saved);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Tenant provisioning failed: Keycloak user could not be created");
        }

        saved.setKeycloakRealm(keycloakRealm);
        tenantRepository.save(saved);
        log.info("Tenant provisioned: tenantCode={}, adminEmail={}", saved.getTenantCode(), request.getAdminEmail());
        return TenantResponse.from(saved);
    }
}
