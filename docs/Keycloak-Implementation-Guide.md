# Keycloak Implementation Guide

## Overview

This guide provides implementation steps for integrating Keycloak RBAC into Werkflow platform components.

## Table of Contents

1. [Backend Integration (Spring Boot)](#backend-integration-spring-boot)
2. [Frontend Integration (Next.js)](#frontend-integration-nextjs)
3. [Workflow Task Assignment](#workflow-task-assignment)
4. [Testing](#testing)
5. [Production Deployment](#production-deployment)

---

## Backend Integration (Spring Boot)

### 1. Dependencies

Add to `/services/engine/pom.xml`:

```xml
<!-- Keycloak Admin Client -->
<dependency>
    <groupId>org.keycloak</groupId>
    <artifactId>keycloak-admin-client</artifactId>
    <version>24.0.4</version>
</dependency>
```

### 2. Configuration

Update `/services/engine/src/main/resources/application.yml`:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER:http://keycloak:8080/realms/werkflow-platform}
          jwk-set-uri: ${KEYCLOAK_ISSUER:http://keycloak:8080/realms/werkflow-platform}/protocol/openid-connect/certs

keycloak:
  auth-server-url: ${KEYCLOAK_AUTH_SERVER_URL:http://keycloak:8080}
  realm: ${KEYCLOAK_REALM:werkflow-platform}
  admin:
    client-id: ${KEYCLOAK_ADMIN_CLIENT_ID:werkflow-engine}
    client-secret: ${KEYCLOAK_ADMIN_CLIENT_SECRET:REDACTED_ROTATE_THIS_SECRET}
```

### 3. Security Configuration

The existing `SecurityConfig.java` already handles JWT authentication. Update to use the new role extractor:

```java
package com.werkflow.engine.config;

import com.werkflow.engine.security.KeycloakRoleExtractor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final KeycloakRoleExtractor roleExtractor;

    public SecurityConfig(KeycloakRoleExtractor roleExtractor) {
        this.roleExtractor = roleExtractor;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roleExtractor::extractAuthorities);
        return converter;
    }

    // ... rest of configuration
}
```

### 4. Controller Authorization

Use `@PreAuthorize` annotations:

```java
package com.werkflow.engine.controller;

import com.werkflow.engine.security.WorkflowAuthorizationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workflows/asset-requests")
public class AssetRequestController {

    private final WorkflowAuthorizationService authService;

    // Submit asset request
    @PostMapping
    @PreAuthorize("hasAnyRole('ASSET_REQUEST_REQUESTER', 'EMPLOYEE')")
    public ResponseEntity<?> submitRequest(
        @RequestBody AssetRequestDTO request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        // Verify authorization
        if (!authService.canSubmitAssetRequest(jwt)) {
            return ResponseEntity.status(403).body("Not authorized to submit requests");
        }

        // Process request
        // ...
    }

    // Approve asset request
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('ASSET_REQUEST_APPROVER')")
    public ResponseEntity<?> approveRequest(
        @PathVariable String id,
        @RequestBody ApprovalDTO approval,
        @AuthenticationPrincipal Jwt jwt
    ) {
        // Additional authorization checks
        if (!authService.canApproveAssetRequest(jwt)) {
            return ResponseEntity.status(403).body("Not authorized to approve");
        }

        // Process approval
        // ...
    }

    // Finance DOA approval
    @PutMapping("/{id}/approve-finance")
    @PreAuthorize("hasAnyRole('DOA_APPROVER_LEVEL1', 'DOA_APPROVER_LEVEL2', 'DOA_APPROVER_LEVEL3', 'DOA_APPROVER_LEVEL4')")
    public ResponseEntity<?> approveFinance(
        @PathVariable String id,
        @RequestBody FinanceApprovalDTO approval,
        @AuthenticationPrincipal Jwt jwt
    ) {
        // Check DOA level matches amount
        if (!authService.canApproveByDoaLevel(jwt, approval.getAmount())) {
            return ResponseEntity.status(403)
                .body("Insufficient DOA level for amount: $" + approval.getAmount());
        }

        // Process finance approval
        // ...
    }
}
```

### 5. Task Assignment in Flowable

Create Flowable task listener to auto-assign tasks:

```java
package com.werkflow.engine.listener;

import com.werkflow.engine.security.WorkflowTaskRouter;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("autoAssignTaskListener")
public class AutoAssignTaskListener implements TaskListener {

    private final WorkflowTaskRouter taskRouter;

    public AutoAssignTaskListener(WorkflowTaskRouter taskRouter) {
        this.taskRouter = taskRouter;
    }

    @Override
    public void notify(DelegateTask delegateTask) {
        String workflowKey = delegateTask.getProcessDefinitionId().split(":")[0];
        String taskKey = delegateTask.getTaskDefinitionKey();

        // Get task candidates
        List<String> candidates = taskRouter.getTaskCandidates(
            workflowKey,
            taskKey,
            delegateTask.getVariables()
        );

        if (candidates.size() == 1) {
            // Single candidate - assign directly
            delegateTask.setAssignee(candidates.get(0));
        } else {
            // Multiple candidates - set as candidate users
            delegateTask.addCandidateUsers(candidates);
        }
    }
}
```

Use in BPMN:

```xml
<userTask id="lineManagerApproval" name="Line Manager Approval"
          flowable:assignee="${autoAssignTaskListener}">
    <extensionElements>
        <flowable:taskListener event="create" delegateExpression="${autoAssignTaskListener}" />
    </extensionElements>
</userTask>
```

---

## Frontend Integration (Next.js)

### 1. NextAuth Configuration

Update `/frontends/portal/auth.config.ts`:

```typescript
import type { NextAuthConfig } from "next-auth";
import Keycloak from "next-auth/providers/keycloak";

export const authConfig = {
  providers: [
    Keycloak({
      clientId: process.env.KEYCLOAK_CLIENT_ID!,
      clientSecret: process.env.KEYCLOAK_CLIENT_SECRET!,
      issuer: process.env.KEYCLOAK_ISSUER_INTERNAL!,
      authorization: {
        url: `${process.env.KEYCLOAK_ISSUER_BROWSER}/protocol/openid-connect/auth`,
        params: {
          scope: "openid email profile",
        },
      },
    }),
  ],
  callbacks: {
    async jwt({ token, account, profile }) {
      if (account && profile) {
        token.accessToken = account.access_token;
        token.idToken = account.id_token;
        token.roles = profile.realm_access?.roles || [];
        token.groups = profile.groups || [];
        token.department = profile.department;
        token.doaLevel = profile.doa_level;
        token.employeeId = profile.employee_id;
        token.isPoc = profile.is_poc;
      }
      return token;
    },
    async session({ session, token }) {
      session.accessToken = token.accessToken as string;
      session.user.roles = token.roles as string[];
      session.user.groups = token.groups as string[];
      session.user.department = token.department as string;
      session.user.doaLevel = token.doaLevel as number;
      session.user.employeeId = token.employeeId as string;
      session.user.isPoc = token.isPoc as boolean;
      return session;
    },
  },
} satisfies NextAuthConfig;
```

### 2. Role-Based UI Components

Create authorization hooks:

```typescript
// /frontends/portal/hooks/useAuthorization.ts
import { useSession } from "next-auth/react";

export function useAuthorization() {
  const { data: session } = useSession();

  const hasRole = (role: string): boolean => {
    return session?.user?.roles?.includes(role) || false;
  };

  const hasAnyRole = (...roles: string[]): boolean => {
    return roles.some(role => hasRole(role));
  };

  const hasAllRoles = (...roles: string[]): boolean => {
    return roles.every(role => hasRole(role));
  };

  const isMemberOfGroup = (group: string): boolean => {
    return session?.user?.groups?.includes(group) || false;
  };

  const canSubmitAssetRequest = (): boolean => {
    return hasAnyRole("asset_request_requester", "employee");
  };

  const canApproveAssetRequest = (): boolean => {
    return hasRole("asset_request_approver");
  };

  const canApproveByDoa = (amount: number): boolean => {
    const doaLevel = session?.user?.doaLevel || 0;
    if (amount <= 1000) return doaLevel >= 1;
    if (amount <= 10000) return doaLevel >= 2;
    if (amount <= 100000) return doaLevel >= 3;
    return doaLevel >= 4;
  };

  const isDepartmentPoc = (): boolean => {
    return session?.user?.isPoc === true && hasRole("department_poc");
  };

  return {
    session,
    hasRole,
    hasAnyRole,
    hasAllRoles,
    isMemberOfGroup,
    canSubmitAssetRequest,
    canApproveAssetRequest,
    canApproveByDoa,
    isDepartmentPoc,
  };
}
```

### 3. Protected Components

```typescript
// /frontends/portal/components/ProtectedComponent.tsx
import { useAuthorization } from "@/hooks/useAuthorization";

interface Props {
  requiredRole?: string;
  requiredRoles?: string[];
  requiredGroup?: string;
  children: React.ReactNode;
  fallback?: React.ReactNode;
}

export function ProtectedComponent({
  requiredRole,
  requiredRoles,
  requiredGroup,
  children,
  fallback = null,
}: Props) {
  const { hasRole, hasAnyRole, isMemberOfGroup } = useAuthorization();

  let isAuthorized = true;

  if (requiredRole && !hasRole(requiredRole)) {
    isAuthorized = false;
  }

  if (requiredRoles && !hasAnyRole(...requiredRoles)) {
    isAuthorized = false;
  }

  if (requiredGroup && !isMemberOfGroup(requiredGroup)) {
    isAuthorized = false;
  }

  return isAuthorized ? <>{children}</> : <>{fallback}</>;
}
```

### 4. Usage in Pages

```typescript
// /frontends/portal/app/workflows/asset-requests/page.tsx
import { ProtectedComponent } from "@/components/ProtectedComponent";
import { useAuthorization } from "@/hooks/useAuthorization";

export default function AssetRequestsPage() {
  const { canSubmitAssetRequest, canApproveAssetRequest } = useAuthorization();

  return (
    <div>
      <h1>Asset Requests</h1>

      {/* Show submit button only if authorized */}
      <ProtectedComponent requiredRoles={["asset_request_requester", "employee"]}>
        <button onClick={handleSubmit}>Submit New Request</button>
      </ProtectedComponent>

      {/* Show approval section only for approvers */}
      <ProtectedComponent requiredRole="asset_request_approver">
        <div>
          <h2>Pending Approvals</h2>
          {/* Approval list */}
        </div>
      </ProtectedComponent>
    </div>
  );
}
```

---

## Workflow Task Assignment

### 1. BPMN Configuration

Example Asset Request workflow with role-based assignment:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn">

  <process id="asset_request" name="Asset Request Workflow">

    <!-- Task 1: Submit Request -->
    <startEvent id="start" />

    <!-- Task 2: Line Manager Approval -->
    <userTask id="lineManagerApproval" name="Line Manager Approval"
              flowable:candidateUsers="${autoAssignTaskListener}">
      <extensionElements>
        <flowable:taskListener event="create" delegateExpression="${autoAssignTaskListener}" />
      </extensionElements>
    </userTask>

    <!-- Task 3: IT Approval -->
    <userTask id="itApproval" name="IT Department Approval"
              flowable:candidateGroups="/IT Department/Managers,/IT Department/POC">
      <extensionElements>
        <flowable:taskListener event="create" delegateExpression="${autoAssignTaskListener}" />
      </extensionElements>
    </userTask>

    <!-- Task 4: Finance DOA Approval -->
    <userTask id="financeApproval" name="Finance DOA Approval"
              flowable:candidateUsers="${autoAssignTaskListener}">
      <extensionElements>
        <flowable:taskListener event="create" delegateExpression="${autoAssignTaskListener}" />
      </extensionElements>
    </userTask>

  </process>
</definitions>
```

### 2. Task Query with Authorization

Query tasks user can access:

```java
@Service
public class TaskQueryService {

    private final TaskService taskService;
    private final KeycloakRoleExtractor roleExtractor;

    public List<Task> getMyTasks(Jwt jwt) {
        String userId = roleExtractor.getUserId(jwt);
        List<String> groups = roleExtractor.extractGroups(jwt);

        // Query tasks assigned to user or candidate groups
        TaskQuery query = taskService.createTaskQuery()
            .or()
                .taskAssignee(userId)
                .taskCandidateUser(userId)
                .taskCandidateGroupIn(groups)
            .endOr()
            .orderByTaskCreateTime()
            .desc();

        return query.list();
    }

    public List<Task> getTasksForApproval(Jwt jwt) {
        String userId = roleExtractor.getUserId(jwt);

        // Only tasks user can approve
        return taskService.createTaskQuery()
            .taskCandidateOrAssigned(userId)
            .processVariableValueEquals("status", "pending_approval")
            .orderByTaskPriority()
            .desc()
            .list();
    }
}
```

---

## Testing

### 1. Unit Tests

Test authorization service:

```java
@SpringBootTest
class WorkflowAuthorizationServiceTest {

    @Autowired
    private WorkflowAuthorizationService authService;

    @Test
    void testCanSubmitAssetRequest() {
        // Create mock JWT with employee role
        Jwt jwt = createMockJwt(List.of("employee"), List.of(), Map.of());

        assertTrue(authService.canSubmitAssetRequest(jwt));
    }

    @Test
    void testDoaLevelApproval() {
        // Create mock JWT with DOA level 2
        Jwt jwt = createMockJwt(
            List.of("doa_approver_level2"),
            List.of("/Finance Department/Approvers"),
            Map.of("doa_level", 2)
        );

        // Can approve $5,000 (requires level 2)
        assertTrue(authService.canApproveByDoaLevel(jwt, new BigDecimal("5000")));

        // Cannot approve $50,000 (requires level 3)
        assertFalse(authService.canApproveByDoaLevel(jwt, new BigDecimal("50000")));
    }

    private Jwt createMockJwt(List<String> roles, List<String> groups, Map<String, Object> attributes) {
        // Mock JWT creation
        // ...
    }
}
```

### 2. Integration Tests

Test end-to-end workflow with Keycloak:

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AssetRequestWorkflowIntegrationTest {

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Test
    void testAssetRequestWorkflow() {
        // Start workflow as employee
        String employeeToken = getKeycloakToken("john.employee", "password");

        ProcessInstance process = runtimeService.startProcessInstanceByKey(
            "asset_request",
            Map.of("submitter_user_id", "employee-uuid")
        );

        // Verify task assigned to manager
        Task managerTask = taskService.createTaskQuery()
            .processInstanceId(process.getId())
            .taskDefinitionKey("lineManagerApproval")
            .singleResult();

        assertNotNull(managerTask);
        assertEquals("manager-uuid", managerTask.getAssignee());

        // Complete manager approval
        String managerToken = getKeycloakToken("jane.manager", "password");
        taskService.complete(managerTask.getId(), Map.of("approved", true));

        // Verify next task routed to IT
        // ...
    }

    private String getKeycloakToken(String username, String password) {
        // Get token from Keycloak
        // ...
    }
}
```

---

## Production Deployment

### 1. Environment Variables

Set in production environment:

```bash
# Keycloak Configuration
KEYCLOAK_ISSUER=https://keycloak.company.com/realms/werkflow-platform
KEYCLOAK_AUTH_SERVER_URL=https://keycloak.company.com
KEYCLOAK_REALM=werkflow-platform
KEYCLOAK_ADMIN_CLIENT_ID=werkflow-engine
KEYCLOAK_ADMIN_CLIENT_SECRET=<secure-secret>

# Frontend (Portal)
NEXTAUTH_URL=https://portal.company.com
KEYCLOAK_ISSUER_INTERNAL=http://keycloak:8080/realms/werkflow-platform
KEYCLOAK_ISSUER_PUBLIC=https://keycloak.company.com/realms/werkflow-platform
KEYCLOAK_ISSUER_BROWSER=https://keycloak.company.com/realms/werkflow-platform
KEYCLOAK_CLIENT_ID=werkflow-portal
KEYCLOAK_CLIENT_SECRET=<secure-secret>
```

### 2. Keycloak Configuration

1. **Import Realm**:
   ```bash
   docker exec -it keycloak /opt/keycloak/bin/kc.sh import \
     --file /opt/keycloak/data/import/werkflow-realm.json \
     --override true
   ```

2. **Update Client Secrets**:
   - Generate secure secrets
   - Update in Keycloak Admin Console
   - Update in application environment variables

3. **Configure HTTPS**:
   - Set up reverse proxy (Nginx/Apache)
   - Configure SSL certificates
   - Update Keycloak hostname configuration

### 3. Database Migration

Run Flyway migrations:

```bash
cd /services/engine
mvn flyway:migrate
```

### 4. Import Initial Users

Use Keycloak Admin API or bulk import:

```bash
# Import users from JSON
curl -X POST "https://keycloak.company.com/admin/realms/werkflow-platform/users" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d @users-import.json
```

### 5. Monitoring

Set up monitoring for:
- Failed authentication attempts
- Authorization denials
- Token validation errors
- Keycloak availability

Use Prometheus + Grafana with Keycloak metrics endpoint.

---

## Operations Quick Reference

### Adding a New User

1. Login to Keycloak Admin Console (`http://localhost:8090`)
2. Select `werkflow-platform` realm
3. Users -> Add User -> fill username, email, first/last name
4. Credentials tab -> Set Password (Temporary: OFF)
5. Role Mappings tab -> Assign roles (e.g., `HR_STAFF`, `EMPLOYEE`)
6. If using groups: Groups tab -> Join group (e.g., `/HR Department/Staff`)

### Managing Roles

- Realm Roles -> Create Role -> set name and description
- Composite roles: open a role -> Composite Roles tab -> add sub-roles
- Role naming: `DEPARTMENT_LEVEL` (e.g., `HR_ADMIN`, `FINANCE_STAFF`)

### Custom User Attributes

Set on user's Attributes tab:
- `employee_id`: Employee identifier
- `department`: Department name
- `doa_level`: Delegation of Authority level (1-4)
- `is_poc`: Point of Contact flag

### Service Account — Admin API Access

The admin service calls the Keycloak Admin API to list realm roles for tenant setup dropdowns (`GET /api/v1/keycloak/realm-roles`). It authenticates using the `werkflow-portal` client's service account via client credentials (not a user token).

**How it works:**

1. Admin service calls `POST /realms/werkflow/protocol/openid-connect/token` with `grant_type=client_credentials` using the `werkflow-portal` client ID and secret.
2. It uses that token to call `GET /admin/realms/werkflow/roles` — the Keycloak Admin API.
3. The response is filtered (removes `offline_access`, `uma_authorization`, `default-roles-*`) and returned as `{ "roles": [...] }`.

**Required setup:** The `werkflow-portal` service account must be granted two roles from the `realm-management` client:

| Role | Purpose |
|------|---------|
| `view-realm` | Read realm configuration — roles, clients, identity providers |
| `query-users` | List and search users (needed for future user-lookup features) |

These are already declared in `infrastructure/keycloak/realms/werkflow-realm.json`. However, **Keycloak only applies the realm JSON on first boot** — if the Keycloak postgres volume already exists from a previous start, the running instance will not pick up changes to the JSON automatically.

**To grant manually on a running instance:**

1. Open Keycloak Admin Console (`http://localhost:8090`)
2. Select the **werkflow** realm
3. Go to **Clients** → `werkflow-portal` → **Service Account Roles** tab
4. Click **Assign role** → filter by client: `realm-management`
5. Select `view-realm` and `query-users` → **Assign**

No restart required — changes take effect immediately.

**Environment variables (admin service):**

| Variable | Value | Notes |
|----------|-------|-------|
| `KEYCLOAK_ADMIN_URL` | `http://keycloak:8080` | Internal Docker URL for Admin API calls |
| `KEYCLOAK_URL` | `http://localhost:8090` | External URL for token issuer validation |
| `KEYCLOAK_CLIENT_ID` | `werkflow-portal` | Client used for service account credentials |
| `KEYCLOAK_CLIENT_SECRET` | (32-char secret) | Must match `secret` in `werkflow-realm.json` exactly |

**Diagnosing failures:**

| Admin service log | Meaning | Fix |
|-------------------|---------|-----|
| `401 Unauthorized` | Wrong client secret | Check `KEYCLOAK_CLIENT_SECRET` matches realm.json (full 32 chars); rebuild admin service |
| `403 Forbidden: unknown_error` | Token valid but service account lacks permissions | Assign `view-realm` + `query-users` via Keycloak Admin Console |
| `Connection refused` | Wrong `KEYCLOAK_ADMIN_URL` | Must be internal Docker hostname (`http://keycloak:8080`), not `localhost` |

The UI symptom in all three cases is the same — realm roles dropdown shows empty — because `KeycloakUserService.listRealmRoles()` catches all exceptions and returns an empty list to avoid breaking the endpoint.

### Common Troubleshooting

- **401 after login**: Check user has required roles assigned
- **Token issuer mismatch**: Verify `KEYCLOAK_ISSUER` env var matches realm URL
- **Redirect loop**: Check Valid Redirect URIs includes `http://localhost:4000/*`
- **Client not found**: Verify `werkflow-portal` client exists in realm
- **Realm roles dropdown empty**: See Service Account — Admin API Access section above

---

## References

- Keycloak RBAC Role Matrix: [Keycloak-RBAC-Role-Matrix-Design.md](./Keycloak-RBAC-Role-Matrix-Design.md)
- Realm Configuration: `/infrastructure/keycloak/realms/werkflow-realm.json`
