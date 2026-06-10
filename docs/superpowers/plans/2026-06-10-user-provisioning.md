# User Provisioning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure 1 super admin exists on every fresh deploy, 1 tenant admin is auto-created when a tenant is provisioned, and tenant users can be created and invited via the portal Users page.

**Architecture:** Three-layer provisioning — (1) Flyway seed migration for the platform super admin, (2) `TenantProvisioningService` extended to write Org + User DB rows after KC user creation, (3) new `POST /api/users/invite` endpoint (KC user + admin DB row in one call) backed by a new portal `/admin/tenant/users` page. `UserService.createUser` (existing `POST /api/users`) is left for registering pre-existing KC users; the invite endpoint is the path for new user creation. No auto-upsert on `getUserProfile` — that is the wrong pattern per the architecture review.

**Tech Stack:** Spring Boot 3.3.x · Java 21 · JPA/Hibernate · Flyway · Keycloak 26 Admin REST API · Next.js 14 · React Query · shadcn/ui

---

## Architecture decisions captured from review

| Decision | Rationale |
|---|---|
| `keycloak_id` stores `preferred_username` (= email for provisioned users, `"admin"` for super admin) | Engine's `JwtUserContext.userId = preferred_username`; lookup key must match |
| V29 Flyway migration, not `@PostConstruct` | Schema dependency on V1 org seed; idempotent by design; auditable |
| One `Organization` row per tenant | `organization_id NOT NULL` requires a concrete FK; default org is for super admin only |
| KC call stays outside `@Transactional` | KC is non-transactional; DB writes wrapped in transaction separately; compensating delete on KC failure |
| `createUser` (`POST /api/users`) = register existing KC user | Existing contract; `keycloakId` required; no KC call |
| `inviteUser` (`POST /api/users/invite`) = new user end-to-end | Creates KC user (invite email), then admin DB row; compensation on failure |
| No auto-upsert on `getUserProfile` | Write on read path; `organization_id NOT NULL` unsatisfiable from JWT; race condition risk |
| `Organization.tenantCode` must be set explicitly | JPA entity has no `nullable=false` but DDL does; silent constraint failure otherwise |

## Known limitations (not fixed in this plan)

- **Caffeine cache poisoning:** `AdminServiceClient.getUserProfile` in the engine caches null. If the engine calls before a user is provisioned, the null is cached until TTL. Not blocking — TTL-based expiry is acceptable for MVP.
- **KC rollback gap:** If the second `tenantRepository.save` (setting `keycloakRealm`) fails after KC user was created, the tenant row exists with `keycloakRealm = null` and there is no compensating KC delete. Pre-existing gap — not introduced by this plan.
- **`KeycloakUserInfo` stubs** (lines 196–283 in `KeycloakUserService`) are hardcoded fixtures. Not fixed here — tracked as a separate pre-production item.

---

## File Map

**New files:**
- `services/admin/src/main/resources/db/migration/V29__super_admin_user_seed.sql`
- `services/admin/src/main/java/com/werkflow/admin/dto/UserInviteRequest.java`
- `services/admin/src/test/java/com/werkflow/admin/service/TenantProvisioningServiceTest.java`
- `services/admin/src/test/java/com/werkflow/admin/service/UserInviteServiceTest.java`
- `frontends/portal/app/(platform)/admin/tenant/users/page.tsx`

**Modified files:**
- `services/admin/src/main/java/com/werkflow/admin/service/TenantProvisioningService.java` — inject Org + User repos; write rows after KC; `@Transactional` on DB writes
- `services/admin/src/main/java/com/werkflow/admin/service/KeycloakUserService.java` — extract `createKeycloakUser(email, firstName, lastName, tenantId, roleName)` from `createTenantAdminUser`
- `services/admin/src/main/java/com/werkflow/admin/service/UserService.java` — add `inviteUser(UserInviteRequest, callerTenantCode)`
- `services/admin/src/main/java/com/werkflow/admin/controller/UserController.java` — add `POST /invite` endpoint
- `services/admin/src/main/java/com/werkflow/admin/repository/OrganizationRepository.java` — add `findByTenantCode`

---

## Task 1: V29 super admin seed migration

**Files:**
- Create: `services/admin/src/main/resources/db/migration/V29__super_admin_user_seed.sql`

- [ ] **Step 1: Write the migration**

Create `services/admin/src/main/resources/db/migration/V29__super_admin_user_seed.sql`:

```sql
-- Seeds the platform super admin user for fresh deployments.
-- Idempotent: ON CONFLICT DO NOTHING — safe to run on every restart.
-- Resolves organization_id and role_id by name (not hardcoded numeric ID).
-- 'Werkflow Organisation' is seeded in V1. 'SUPER_ADMIN' role is seeded in V1.

INSERT INTO users (
    keycloak_id,
    username,
    email,
    first_name,
    last_name,
    organization_id,
    tenant_code,
    doa_level,
    active,
    email_verified,
    created_at,
    updated_at
)
SELECT
    'admin',
    'admin',
    'admin@werkflow.internal',
    'Admin',
    'User',
    o.id,
    'default',
    4,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM organizations o
WHERE o.name = 'Werkflow Organisation'
ON CONFLICT (keycloak_id) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
CROSS JOIN roles r
WHERE u.keycloak_id = 'admin'
  AND r.name = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;
```

- [ ] **Step 2: Verify migration runs clean**

```bash
mvn flyway:migrate -f services/admin/pom.xml \
  -Dflyway.url=jdbc:postgresql://localhost:5433/werkflow \
  -Dflyway.schemas=admin_service \
  -Dflyway.user=werkflow_admin \
  -Dflyway.password=<your-db-password>
```

Expected output: `Successfully applied 1 migration to schema "admin_service"` and `Current version of schema "admin_service": 29`

- [ ] **Step 3: Verify the row exists**

```sql
SELECT keycloak_id, username, email, tenant_code, doa_level
FROM admin_service.users
WHERE keycloak_id = 'admin';
-- Expected: 1 row with doa_level=4, tenant_code='default'

SELECT u.keycloak_id, r.name
FROM admin_service.users u
JOIN admin_service.user_roles ur ON ur.user_id = u.id
JOIN admin_service.roles r ON r.id = ur.role_id
WHERE u.keycloak_id = 'admin';
-- Expected: 1 row with role name 'SUPER_ADMIN'
```

- [ ] **Step 4: Verify idempotency — run migration a second time**

Re-run the same `mvn flyway:migrate` command. Flyway will skip V29 (already applied). No duplicate row error. Query the table again — still exactly 1 admin row.

- [ ] **Step 5: Commit**

```bash
git add services/admin/src/main/resources/db/migration/V29__super_admin_user_seed.sql
git commit -m "feat(admin): V29 seed super admin user for fresh deploys"
```

---

## Task 2: Add `findByTenantCode` to `OrganizationRepository`

**Files:**
- Modify: `services/admin/src/main/java/com/werkflow/admin/repository/OrganizationRepository.java`

This query is needed by both Task 3 and Task 4.

- [ ] **Step 1: Add the method**

In `OrganizationRepository.java`, add after `existsByName`:

```java
Optional<Organization> findByTenantCode(String tenantCode);
```

- [ ] **Step 2: Verify compile**

```bash
mvn compile -f services/admin/pom.xml -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add services/admin/src/main/java/com/werkflow/admin/repository/OrganizationRepository.java
git commit -m "feat(admin): add findByTenantCode to OrganizationRepository"
```

---

## Task 3: Generalise `KeycloakUserService.createTenantAdminUser`

**Files:**
- Modify: `services/admin/src/main/java/com/werkflow/admin/service/KeycloakUserService.java`

Extract the core logic of `createTenantAdminUser` into a reusable `createKeycloakUser` method so both `TenantProvisioningService` (Task 4) and `UserService.inviteUser` (Task 5) can call it with any role name.

- [ ] **Step 1: Add `createKeycloakUser` method**

In `KeycloakUserService.java`, add this public method immediately above `createTenantAdminUser`:

```java
/**
 * Creates a Keycloak user, assigns one realm role, and sends the invite email.
 * The KC username is set to {@code email} — consistent with how preferred_username
 * is used as the lookup key in the admin DB (keycloak_id = preferred_username = email).
 *
 * @param email     the user's email; also becomes the KC username/preferred_username
 * @param firstName first name
 * @param lastName  last name
 * @param tenantId  value to set as the 'tenant_id' KC user attribute
 * @param roleName  realm role to assign (e.g. "admin", "employee")
 * @throws IllegalStateException if KC user creation, role lookup, or role assignment fails
 */
public void createKeycloakUser(
        String email, String firstName, String lastName,
        String tenantId, String roleName) {
    String token = fetchServiceAccountToken();

    String createUrl = keycloakAdminUrl + "/admin/realms/" + keycloakRealm + "/users";
    Map<String, Object> userRepresentation = Map.of(
            "username", email,
            "email", email,
            "firstName", firstName != null ? firstName : "",
            "lastName", lastName != null ? lastName : "",
            "enabled", true,
            "emailVerified", false,
            "attributes", Map.of("tenant_id", List.of(tenantId)),
            "requiredActions", List.of("UPDATE_PASSWORD", "VERIFY_EMAIL")
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(token);

    ResponseEntity<Void> createResponse = restTemplate.exchange(
            createUrl, HttpMethod.POST,
            new HttpEntity<>(userRepresentation, headers), Void.class);

    if (!createResponse.getStatusCode().is2xxSuccessful()) {
        throw new IllegalStateException(
                "Keycloak user creation failed with status: " + createResponse.getStatusCode());
    }

    String userId = findKeycloakUserIdByEmail(email, token);
    assignRealmRole(userId, roleName, token);

    String actionsEmailUrl = keycloakAdminUrl + "/admin/realms/" + keycloakRealm
            + "/users/" + userId + "/execute-actions-email";
    try {
        restTemplate.exchange(actionsEmailUrl, HttpMethod.PUT,
                new HttpEntity<>(List.of("UPDATE_PASSWORD", "VERIFY_EMAIL"), headers), Void.class);
    } catch (Exception e) {
        log.warn("Failed to send invite email to {} (SMTP may not be configured): {}", email, e.getMessage());
    }

    log.info("Keycloak user created: email={}, tenantId={}, role={}", email, tenantId, roleName);
}
```

- [ ] **Step 2: Refactor `createTenantAdminUser` to delegate**

Replace the body of `createTenantAdminUser` with a delegation call:

```java
public void createTenantAdminUser(String email, String firstName, String lastName, String tenantId) {
    createKeycloakUser(email, firstName, lastName, tenantId, "admin");
}
```

- [ ] **Step 3: Verify compile**

```bash
mvn compile -f services/admin/pom.xml -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Run existing tests to confirm no regression**

```bash
mvn test -f services/admin/pom.xml -q
```

Expected: BUILD SUCCESS (all existing tests pass)

- [ ] **Step 5: Commit**

```bash
git add services/admin/src/main/java/com/werkflow/admin/service/KeycloakUserService.java
git commit -m "refactor(admin): extract createKeycloakUser from createTenantAdminUser"
```

---

## Task 4: Fix `TenantProvisioningService` — create Org + User DB rows

**Files:**
- Modify: `services/admin/src/main/java/com/werkflow/admin/service/TenantProvisioningService.java`
- Create: `services/admin/src/test/java/com/werkflow/admin/service/TenantProvisioningServiceTest.java`

**Context:** Currently `TenantProvisioningService` creates the KC user but never writes an Organization or User row. This means tenant admins get a 404 on `getUserProfile`. The fix creates one Org row per tenant and one User row for the tenant admin.

- [ ] **Step 1: Write the failing test**

Create `services/admin/src/test/java/com/werkflow/admin/service/TenantProvisioningServiceTest.java`:

```java
package com.werkflow.admin.service;

import com.werkflow.admin.dto.TenantProvisioningRequest;
import com.werkflow.admin.entity.Organization;
import com.werkflow.admin.entity.Role;
import com.werkflow.admin.entity.Tenant;
import com.werkflow.admin.entity.User;
import com.werkflow.admin.repository.OrganizationRepository;
import com.werkflow.admin.repository.RoleRepository;
import com.werkflow.admin.repository.TenantRepository;
import com.werkflow.admin.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantProvisioningServiceTest {

    @Mock TenantRepository tenantRepository;
    @Mock OrganizationRepository organizationRepository;
    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock KeycloakUserService keycloakUserService;

    @InjectMocks
    TenantProvisioningService service;

    private TenantProvisioningRequest request;

    @BeforeEach
    void setUp() {
        request = new TenantProvisioningRequest();
        request.setTenantCode("acme");
        request.setName("ACME Corp");
        request.setAdminEmail("admin@acme.com");
        request.setAdminFirstName("Alice");
        request.setAdminLastName("Admin");
    }

    @Test
    void provision_createsOrgAndAdminUserInDb() {
        when(tenantRepository.existsByTenantCode("acme")).thenReturn(false);
        Tenant savedTenant = new Tenant();
        savedTenant.setTenantCode("acme");
        when(tenantRepository.save(any(Tenant.class))).thenReturn(savedTenant);

        Organization savedOrg = Organization.builder()
                .name("ACME Corp").tenantCode("acme").active(true).build();
        savedOrg = mock(Organization.class);
        when(savedOrg.getId()).thenReturn(1L);
        when(organizationRepository.save(any(Organization.class))).thenReturn(savedOrg);

        Role adminRole = new Role();
        adminRole.setName("ADMIN");
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));

        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.provision(request);

        // Org was saved with correct tenantCode
        ArgumentCaptor<Organization> orgCaptor = ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).save(orgCaptor.capture());
        assertThat(orgCaptor.getValue().getTenantCode()).isEqualTo("acme");
        assertThat(orgCaptor.getValue().getName()).isEqualTo("ACME Corp");

        // User was saved with email as keycloakId and username
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getKeycloakId()).isEqualTo("admin@acme.com");
        assertThat(savedUser.getUsername()).isEqualTo("admin@acme.com");
        assertThat(savedUser.getEmail()).isEqualTo("admin@acme.com");
        assertThat(savedUser.getTenantCode()).isEqualTo("acme");
        assertThat(savedUser.getDoaLevel()).isEqualTo(3);
        assertThat(savedUser.getRoles()).contains(adminRole);

        // KC user was created
        verify(keycloakUserService).createKeycloakUser(
                "admin@acme.com", "Alice", "Admin", "acme", "admin");
    }

    @Test
    void provision_compensatesAndThrows_whenKcFails() {
        when(tenantRepository.existsByTenantCode("acme")).thenReturn(false);
        Tenant savedTenant = new Tenant();
        when(tenantRepository.save(any(Tenant.class))).thenReturn(savedTenant);

        Organization savedOrg = mock(Organization.class);
        when(organizationRepository.save(any(Organization.class))).thenReturn(savedOrg);

        Role adminRole = new Role();
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        doThrow(new IllegalStateException("KC down"))
                .when(keycloakUserService).createKeycloakUser(anyString(), anyString(), anyString(), anyString(), anyString());

        assertThatThrownBy(() -> service.provision(request))
                .hasMessageContaining("Tenant provisioning failed");

        // Compensating deletes run
        verify(tenantRepository).delete(savedTenant);
        verify(organizationRepository).delete(savedOrg);
    }

    @Test
    void provision_throwsConflict_whenTenantCodeExists() {
        when(tenantRepository.existsByTenantCode("acme")).thenReturn(true);
        assertThatThrownBy(() -> service.provision(request))
                .hasMessageContaining("already exists");
        verifyNoInteractions(organizationRepository, userRepository, keycloakUserService);
    }
}
```

- [ ] **Step 2: Run the test — expect FAIL**

```bash
mvn test -f services/admin/pom.xml -Dtest=TenantProvisioningServiceTest -q
```

Expected: FAIL — `TenantProvisioningService` does not inject `OrganizationRepository`, `UserRepository`, or `RoleRepository`.

- [ ] **Step 3: Implement the fix**

Replace the entire body of `TenantProvisioningService.java`:

```java
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
     * Provisions a new tenant: DB rows (Tenant + Organization + admin User) + Keycloak user.
     *
     * DB writes are @Transactional. The KC call is non-transactional by nature and runs
     * after the DB commit. On KC failure, a compensating delete removes the DB rows.
     *
     * Contract: KC username = adminEmail = admin DB keycloak_id = admin DB username.
     * This matches how JwtUserContext.userId = preferred_username = email is used as
     * the lookup key in getUserProfile.
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

        // 4. Create KC user (non-transactional — outside the @Transactional scope)
        // On failure: compensate by deleting DB rows
        try {
            keycloakUserService.createKeycloakUser(
                    request.getAdminEmail(),
                    request.getAdminFirstName(),
                    request.getAdminLastName(),
                    request.getTenantCode(),
                    "admin"
            );
        } catch (Exception e) {
            log.error("Keycloak user creation failed for tenant={}, compensating: {}",
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
```

> **Note on `@Transactional` and KC call:** `@Transactional` wraps the DB writes (steps 1–3). The KC call (step 4) runs after the transaction commits. If KC fails, the compensating deletes in the `catch` block run as new DB operations (outside the original transaction). This is the same compensating-delete pattern the existing code used; the `@Transactional` annotation is added only to ensure the three DB writes are atomic with each other.

- [ ] **Step 4: Run tests — expect PASS**

```bash
mvn test -f services/admin/pom.xml -Dtest=TenantProvisioningServiceTest -q
```

Expected: 3/3 tests PASS

- [ ] **Step 5: Run full admin test suite**

```bash
mvn test -f services/admin/pom.xml -q
```

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add services/admin/src/main/java/com/werkflow/admin/service/TenantProvisioningService.java
git add services/admin/src/test/java/com/werkflow/admin/service/TenantProvisioningServiceTest.java
git commit -m "feat(admin): provision org + admin user DB rows on tenant creation"
```

---

## Task 5: Add `POST /api/users/invite` endpoint

**Files:**
- Create: `services/admin/src/main/java/com/werkflow/admin/dto/UserInviteRequest.java`
- Modify: `services/admin/src/main/java/com/werkflow/admin/service/UserService.java`
- Modify: `services/admin/src/main/java/com/werkflow/admin/controller/UserController.java`
- Create: `services/admin/src/test/java/com/werkflow/admin/service/UserInviteServiceTest.java`

**Context:** `POST /api/users` (existing) registers a pre-existing KC user in the admin DB — caller must supply `keycloakId`. This new endpoint creates the KC user via invite email AND writes the admin DB row in one call. The portal uses this to provision new tenant users.

- [ ] **Step 1: Create `UserInviteRequest` DTO**

Create `services/admin/src/main/java/com/werkflow/admin/dto/UserInviteRequest.java`:

```java
package com.werkflow.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInviteRequest {

    @NotBlank @Email @Size(max = 100)
    private String email;

    @NotBlank @Size(max = 100)
    private String firstName;

    @NotBlank @Size(max = 100)
    private String lastName;

    // Role name as a string (e.g. "ADMIN", "EMPLOYEE") — resolved against roles table
    @NotBlank @Size(max = 50)
    private String roleName;

    // Optional enrichment
    private Integer doaLevel;

    @Size(max = 50)
    private String departmentCode;
}
```

- [ ] **Step 2: Write the failing test**

Create `services/admin/src/test/java/com/werkflow/admin/service/UserInviteServiceTest.java`:

```java
package com.werkflow.admin.service;

import com.werkflow.admin.dto.UserInviteRequest;
import com.werkflow.admin.entity.Organization;
import com.werkflow.admin.entity.Role;
import com.werkflow.admin.entity.User;
import com.werkflow.admin.repository.OrganizationRepository;
import com.werkflow.admin.repository.RoleRepository;
import com.werkflow.admin.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserInviteServiceTest {

    @Mock UserRepository userRepository;
    @Mock OrganizationRepository organizationRepository;
    @Mock RoleRepository roleRepository;
    @Mock KeycloakUserService keycloakUserService;

    @InjectMocks
    UserService userService;

    private UserInviteRequest request;
    private Organization org;
    private Role employeeRole;

    @BeforeEach
    void setUp() {
        request = UserInviteRequest.builder()
                .email("jane@acme.com")
                .firstName("Jane")
                .lastName("Employee")
                .roleName("EMPLOYEE")
                .doaLevel(1)
                .departmentCode("HR")
                .build();

        org = Organization.builder().name("ACME Corp").tenantCode("acme").active(true).build();
        org = mock(Organization.class);
        when(org.getId()).thenReturn(1L);

        employeeRole = new Role();
        employeeRole.setName("EMPLOYEE");
    }

    @Test
    void inviteUser_createsKcAndDbUser() {
        when(organizationRepository.findByTenantCode("acme")).thenReturn(Optional.of(org));
        when(roleRepository.findByName("EMPLOYEE")).thenReturn(Optional.of(employeeRole));
        when(userRepository.existsByEmail("jane@acme.com")).thenReturn(false);
        when(userRepository.existsByKeycloakId("jane@acme.com")).thenReturn(false);
        when(userRepository.existsByUsername("jane@acme.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.inviteUser(request, "acme");

        verify(keycloakUserService).createKeycloakUser(
                "jane@acme.com", "Jane", "Employee", "acme", "employee");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getKeycloakId()).isEqualTo("jane@acme.com");
        assertThat(saved.getUsername()).isEqualTo("jane@acme.com");
        assertThat(saved.getEmail()).isEqualTo("jane@acme.com");
        assertThat(saved.getTenantCode()).isEqualTo("acme");
        assertThat(saved.getDoaLevel()).isEqualTo(1);
        assertThat(saved.getDepartmentCode()).isEqualTo("HR");
        assertThat(saved.getRoles()).contains(employeeRole);
    }

    @Test
    void inviteUser_compensatesDbRow_whenKcFails() {
        when(organizationRepository.findByTenantCode("acme")).thenReturn(Optional.of(org));
        when(roleRepository.findByName("EMPLOYEE")).thenReturn(Optional.of(employeeRole));
        when(userRepository.existsByEmail("jane@acme.com")).thenReturn(false);
        when(userRepository.existsByKeycloakId("jane@acme.com")).thenReturn(false);
        when(userRepository.existsByUsername("jane@acme.com")).thenReturn(false);
        User savedUser = mock(User.class);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        doThrow(new IllegalStateException("KC down"))
                .when(keycloakUserService).createKeycloakUser(anyString(), anyString(), anyString(), anyString(), anyString());

        assertThatThrownBy(() -> userService.inviteUser(request, "acme"))
                .hasMessageContaining("invite failed");

        verify(userRepository).delete(savedUser);
    }

    @Test
    void inviteUser_throwsConflict_whenEmailAlreadyExists() {
        when(userRepository.existsByEmail("jane@acme.com")).thenReturn(true);
        assertThatThrownBy(() -> userService.inviteUser(request, "acme"))
                .hasMessageContaining("already exists");
        verifyNoInteractions(keycloakUserService);
    }
}
```

- [ ] **Step 3: Run the test — expect FAIL**

```bash
mvn test -f services/admin/pom.xml -Dtest=UserInviteServiceTest -q
```

Expected: FAIL — `UserService.inviteUser` does not exist yet.

- [ ] **Step 4: Implement `inviteUser` in `UserService`**

Add the following import to `UserService.java`:
```java
import com.werkflow.admin.dto.UserInviteRequest;
```

Add `KeycloakUserService keycloakUserService;` to the `@RequiredArgsConstructor` fields (add the field):
```java
private final KeycloakUserService keycloakUserService;
```

Add this method to `UserService.java` (after `createUser`):

```java
/**
 * Invites a new user: writes the admin DB row first, then creates the KC user.
 * On KC failure, the DB row is deleted (compensating action).
 *
 * KC username = email = DB keycloak_id = DB username (preferred_username contract).
 *
 * @param request        invite details — email becomes the KC username/preferred_username
 * @param callerTenantCode tenant code extracted from the caller's JWT
 */
@Transactional
public UserResponse inviteUser(UserInviteRequest request, String callerTenantCode) {
    if (userRepository.existsByEmail(request.getEmail())) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "User with email '" + request.getEmail() + "' already exists");
    }
    if (userRepository.existsByKeycloakId(request.getEmail())) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "User with Keycloak ID '" + request.getEmail() + "' already exists");
    }
    if (userRepository.existsByUsername(request.getEmail())) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "User with username '" + request.getEmail() + "' already exists");
    }

    Organization org = organizationRepository.findByTenantCode(callerTenantCode)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Organization not found for tenant: " + callerTenantCode));

    Role role = roleRepository.findByName(request.getRoleName().toUpperCase())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Role not found: " + request.getRoleName()));

    // keycloakId = username = email — preferred_username contract
    User user = User.builder()
            .keycloakId(request.getEmail())
            .username(request.getEmail())
            .email(request.getEmail())
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .organization(org)
            .tenantCode(callerTenantCode)
            .doaLevel(request.getDoaLevel())
            .departmentCode(request.getDepartmentCode())
            .active(true)
            .emailVerified(false)
            .roles(List.of(role))
            .build();
    User saved = userRepository.save(user);

    try {
        keycloakUserService.createKeycloakUser(
                request.getEmail(),
                request.getFirstName(),
                request.getLastName(),
                callerTenantCode,
                request.getRoleName().toLowerCase()
        );
    } catch (Exception e) {
        log.error("KC invite failed for email={}, compensating DB delete: {}", request.getEmail(), e.getMessage());
        userRepository.delete(saved);
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "User invite failed: Keycloak user could not be created");
    }

    log.info("User invited: email={}, tenant={}, role={}", request.getEmail(), callerTenantCode, request.getRoleName());
    return mapToResponse(saved);
}
```

- [ ] **Step 5: Add `POST /invite` to `UserController`**

Add this import to `UserController.java`:
```java
import com.werkflow.admin.dto.UserInviteRequest;
```

Add this endpoint after `createUser`:

```java
@PostMapping("/invite")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Operation(summary = "Invite user", description = "Create KC user + admin DB row via email invite")
public ResponseEntity<UserResponse> inviteUser(
        @Valid @RequestBody UserInviteRequest request,
        @AuthenticationPrincipal Jwt jwt) {
    String callerTenantCode = jwtClaimsExtractor.getTenantId(jwt);
    UserResponse response = userService.inviteUser(request, callerTenantCode);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}
```

- [ ] **Step 6: Run tests — expect PASS**

```bash
mvn test -f services/admin/pom.xml -Dtest=UserInviteServiceTest -q
```

Expected: 3/3 PASS

- [ ] **Step 7: Run full admin test suite**

```bash
mvn test -f services/admin/pom.xml -q
```

Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add services/admin/src/main/java/com/werkflow/admin/dto/UserInviteRequest.java
git add services/admin/src/main/java/com/werkflow/admin/service/UserService.java
git add services/admin/src/main/java/com/werkflow/admin/controller/UserController.java
git add services/admin/src/test/java/com/werkflow/admin/service/UserInviteServiceTest.java
git commit -m "feat(admin): add POST /api/users/invite for end-to-end user provisioning"
```

---

## Task 6: Portal `/admin/tenant/users` page

**Files:**
- Create: `frontends/portal/app/(platform)/admin/tenant/users/page.tsx`

**Context:** No user management UI currently exists in the portal. This page lets a tenant admin list their users and invite new ones. It follows the exact same component pattern as `/admin/platform/tenants/page.tsx` (shadcn Dialog for create, React Query for data, `fetch('/api/proxy/admin/...')` for API calls).

The page calls:
- `GET /api/proxy/admin/users/organization/{orgId}` — list users (needs orgId)
- `POST /api/proxy/admin/users/invite` — invite a new user

To get the `orgId`, the page calls `GET /api/proxy/admin/organizations/by-tenant/{tenantCode}` using the session's tenant code. This requires a new admin service endpoint — see **Task 6a** below.

- [ ] **Step 6a: Add `GET /api/organizations/by-tenant/{tenantCode}` to admin service**

Create `services/admin/src/main/java/com/werkflow/admin/controller/OrganizationController.java` if it does not exist, or add to the existing one:

```java
@GetMapping("/by-tenant/{tenantCode}")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public ResponseEntity<OrganizationResponse> getByTenantCode(
        @PathVariable String tenantCode,
        @AuthenticationPrincipal Jwt jwt) {
    String callerTenant = jwtClaimsExtractor.getTenantId(jwt);
    // SUPER_ADMIN may look up any tenant; ADMIN is scoped to their own
    if (!jwtClaimsExtractor.hasRole(jwt, "SUPER_ADMIN") && !callerTenant.equals(tenantCode)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
    }
    Organization org = organizationRepository.findByTenantCode(tenantCode)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Organization not found for tenant: " + tenantCode));
    return ResponseEntity.ok(OrganizationResponse.from(org));
}
```

Add `OrganizationResponse` DTO (if not present):

```java
// OrganizationResponse.java
package com.werkflow.admin.dto;

import com.werkflow.admin.entity.Organization;
import lombok.Builder;
import lombok.Data;

@Data @Builder
public class OrganizationResponse {
    private Long id;
    private String name;
    private String tenantCode;
    private Boolean active;

    public static OrganizationResponse from(Organization o) {
        return OrganizationResponse.builder()
                .id(o.getId())
                .name(o.getName())
                .tenantCode(o.getTenantCode())
                .active(o.getActive())
                .build();
    }
}
```

Compile check:
```bash
mvn compile -f services/admin/pom.xml -q
```

Commit:
```bash
git add services/admin/src/main/java/com/werkflow/admin/controller/
git add services/admin/src/main/java/com/werkflow/admin/dto/OrganizationResponse.java
git commit -m "feat(admin): expose GET /api/organizations/by-tenant/{tenantCode}"
```

- [ ] **Step 6b: Create the portal Users page**

Create `frontends/portal/app/(platform)/admin/tenant/users/page.tsx`:

```tsx
'use client'

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { useToast } from '@/hooks/use-toast'
import { PageSurface } from '@/components/layout/page-surface'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import { Plus, RefreshCw, User } from 'lucide-react'

interface UserRow {
  id: number
  keycloakId: string
  username: string
  email: string
  firstName: string
  lastName: string
  tenantCode: string
  doaLevel: number | null
  departmentCode: string | null
  active: boolean
  roles: { name: string }[]
}

interface OrgInfo { id: number; name: string; tenantCode: string }
interface InvitePayload {
  email: string; firstName: string; lastName: string
  roleName: string; doaLevel?: number; departmentCode?: string
}

const ROLES = ['ADMIN', 'EMPLOYEE', 'WORKFLOW_ADMIN']

async function fetchOrg(tenantCode: string): Promise<OrgInfo> {
  const res = await fetch(`/api/proxy/admin/organizations/by-tenant/${tenantCode}`)
  if (!res.ok) throw new Error(`Failed to load org: ${res.status}`)
  return res.json()
}

async function fetchUsers(orgId: number): Promise<UserRow[]> {
  const res = await fetch(`/api/proxy/admin/users/organization/${orgId}`)
  if (!res.ok) throw new Error(`Failed to load users: ${res.status}`)
  return res.json()
}

async function inviteUser(payload: InvitePayload): Promise<UserRow> {
  const res = await fetch('/api/proxy/admin/users/invite', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  if (!res.ok) {
    const text = await res.text()
    let msg = `Failed (${res.status})`
    try { msg = JSON.parse(text).message ?? msg } catch { /* use default */ }
    throw new Error(msg)
  }
  return res.json()
}

export default function TenantUsersPage() {
  const { data: session } = useSession()
  const tenantCode: string = (session as any)?.tenantId ?? 'default'
  const { toast } = useToast()
  const queryClient = useQueryClient()

  const [inviteOpen, setInviteOpen] = useState(false)
  const [form, setForm] = useState({
    email: '', firstName: '', lastName: '',
    roleName: 'EMPLOYEE', doaLevel: '', departmentCode: '',
  })
  const [formError, setFormError] = useState<string | null>(null)

  const { data: org } = useQuery<OrgInfo>({
    queryKey: ['org', tenantCode],
    queryFn: () => fetchOrg(tenantCode),
    enabled: !!tenantCode,
  })

  const { data: users = [], isFetching, refetch } = useQuery<UserRow[]>({
    queryKey: ['users', org?.id],
    queryFn: () => fetchUsers(org!.id),
    enabled: !!org?.id,
  })

  const invite = useMutation({
    mutationFn: inviteUser,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users', org?.id] })
      toast({ title: 'Invite sent', description: `${form.email} will receive an email to set their password.` })
      setInviteOpen(false)
      setForm({ email: '', firstName: '', lastName: '', roleName: 'EMPLOYEE', doaLevel: '', departmentCode: '' })
      setFormError(null)
    },
    onError: (e: Error) => setFormError(e.message),
  })

  function handleInvite() {
    setFormError(null)
    invite.mutate({
      email: form.email,
      firstName: form.firstName,
      lastName: form.lastName,
      roleName: form.roleName,
      doaLevel: form.doaLevel ? parseInt(form.doaLevel, 10) : undefined,
      departmentCode: form.departmentCode || undefined,
    })
  }

  return (
    <PageSurface>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-semibold text-foreground">Users</h1>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => refetch()} disabled={isFetching}>
            <RefreshCw className={`h-4 w-4 mr-2 ${isFetching ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
          <Button size="sm" onClick={() => setInviteOpen(true)}>
            <Plus className="h-4 w-4 mr-2" />
            Invite User
          </Button>
        </div>
      </div>

      <div className="space-y-3">
        {users.length === 0 && !isFetching && (
          <Card><CardContent className="py-10 text-center text-muted-foreground">No users yet. Use Invite User to add members.</CardContent></Card>
        )}
        {users.map(u => (
          <Card key={u.id}>
            <CardContent className="py-4 flex items-center justify-between">
              <div className="flex items-center gap-3">
                <User className="h-8 w-8 text-muted-foreground" />
                <div>
                  <p className="font-medium text-sm">{u.firstName} {u.lastName}</p>
                  <p className="text-xs text-muted-foreground">{u.email}</p>
                  {u.departmentCode && <p className="text-xs text-muted-foreground">{u.departmentCode}</p>}
                </div>
              </div>
              <div className="flex items-center gap-2">
                {u.doaLevel && <Badge variant="outline">DOA L{u.doaLevel}</Badge>}
                {u.roles.map(r => <Badge key={r.name} variant="secondary">{r.name}</Badge>)}
                <Badge variant={u.active ? 'default' : 'destructive'}>{u.active ? 'Active' : 'Inactive'}</Badge>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      <Dialog open={inviteOpen} onOpenChange={setInviteOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>Invite User</DialogTitle></DialogHeader>
          <div className="space-y-4 py-2">
            {formError && <p className="text-sm text-destructive">{formError}</p>}
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label>First Name</Label>
                <Input value={form.firstName} onChange={e => setForm(f => ({ ...f, firstName: e.target.value }))} />
              </div>
              <div className="space-y-1">
                <Label>Last Name</Label>
                <Input value={form.lastName} onChange={e => setForm(f => ({ ...f, lastName: e.target.value }))} />
              </div>
            </div>
            <div className="space-y-1">
              <Label>Email</Label>
              <Input type="email" value={form.email} onChange={e => setForm(f => ({ ...f, email: e.target.value }))} />
            </div>
            <div className="space-y-1">
              <Label>Role</Label>
              <Select value={form.roleName} onValueChange={v => setForm(f => ({ ...f, roleName: v }))}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  {ROLES.map(r => <SelectItem key={r} value={r}>{r}</SelectItem>)}
                </SelectContent>
              </Select>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label>DOA Level (optional)</Label>
                <Input type="number" min={1} max={4} value={form.doaLevel} onChange={e => setForm(f => ({ ...f, doaLevel: e.target.value }))} />
              </div>
              <div className="space-y-1">
                <Label>Department (optional)</Label>
                <Input value={form.departmentCode} onChange={e => setForm(f => ({ ...f, departmentCode: e.target.value }))} />
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setInviteOpen(false)}>Cancel</Button>
            <Button onClick={handleInvite} disabled={invite.isPending}>
              {invite.isPending ? 'Sending…' : 'Send Invite'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PageSurface>
  )
}
```

- [ ] **Step 6c: Add the route to the sidebar**

In `frontends/portal/components/layout/sidebar.tsx`, add the Users entry under the tenant admin section (alongside role-mappings):

```tsx
{ labelKey: 'users', href: '/admin/tenant/users', icon: Users, requiredRoles: ['ADMIN', 'SUPER_ADMIN'] },
```

Add the label key to the messages file (`messages/en.json` or equivalent):
```json
"users": "Users"
```

- [ ] **Step 6d: Build check**

```bash
cd frontends/portal && npm run build 2>&1 | tail -20
```

Expected: compiled successfully (no TypeScript errors)

- [ ] **Step 6e: Commit**

```bash
git add frontends/portal/app/\(platform\)/admin/tenant/users/page.tsx
git add frontends/portal/components/layout/sidebar.tsx
git add frontends/portal/messages/
git commit -m "feat(portal): add /admin/tenant/users page for tenant user management"
```

---

## Deferred (not in this plan)

| Item | Reason |
|---|---|
| `/admin/platform/users` — cross-tenant super admin view | KC Admin console covers the use case for MVP |
| `KeycloakUserInfo` stubs (lines 196–283 in `KeycloakUserService`) | Replacement requires real KC Admin API calls for group/DOA lookup — dedicated session |
| Tenant isolation guard on `POST /api/users` (existing endpoint) | Low risk — endpoint is internal; the invite endpoint is the primary user creation path |
| Caffeine cache eviction on user creation | TTL-based expiry acceptable for MVP; cache sits in the engine, not admin service |

---

## End-to-end verification checklist (run after all tasks)

- [ ] Fresh deploy (wipe KC postgres volume only): admin user logs in at `http://localhost:4000` — no `User not found` error in admin service logs
- [ ] Create a tenant via `/admin/platform/tenants/new` → tenant row + org row + admin user row exist in DB; tenant admin receives invite email (or warn if SMTP not configured)
- [ ] Tenant admin logs in → navigates to `/admin/tenant/users` → sees themselves listed
- [ ] Tenant admin invites `jane@acme.com` → user appears in the list with `Active` badge; Keycloak has a user with email `jane@acme.com` and required actions pending
- [ ] `jane@acme.com` logs in after following invite link → portal loads; no `User not found` in logs
