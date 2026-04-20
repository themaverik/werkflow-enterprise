# Task Endpoints Design Specification

**Document Version**: 1.0
**Created**: 2025-11-25
**Status**: Design Approved
**Phase**: 5A Week 2 (Days 2.5-4)
**Priority**: HIGH - Critical for Phase 6 Task UI

---

## Executive Summary

This document provides a comprehensive design for two missing REST API endpoints in the Werkflow Engine Service:

1. `GET /workflows/tasks/my-tasks` - Retrieve tasks assigned to the authenticated user
2. `GET /workflows/tasks/group-tasks` - Retrieve tasks available to user's team/group

These endpoints are foundational for the Phase 6 Task UI implementation and support RBAC, JWT claims extraction, and DOA validation capabilities introduced in Phase 5A.

### Current Issues

- **404 Errors**: Both endpoints return "No static resource workflows/tasks/my-tasks" errors
- **Missing Implementation**: No controllers, services, or DTOs exist for these endpoints
- **JWT Integration Gap**: Need to integrate with JWT claims extraction for department/group filtering

### Design Goals

1. **RBAC Compliance**: Enforce role-based access control using JWT token claims
2. **Performance**: Sub-500ms response times (p95) with caching
3. **Scalability**: Support pagination, filtering, and sorting for large task lists
4. **Authorization**: Users can only see tasks assigned to them or their groups
5. **Extensibility**: Design supports future enhancements (delegation, task history)

---

## Table of Contents

1. [API Specification](#1-api-specification)
2. [Data Models & DTOs](#2-data-models--dtos)
3. [Architecture & Design](#3-architecture--design)
4. [Database Query Strategy](#4-database-query-strategy)
5. [Authorization & Security](#5-authorization--security)
6. [Implementation Plan](#6-implementation-plan)
7. [Testing Strategy](#7-testing-strategy)
8. [Performance Optimization](#8-performance-optimization)
9. [Integration Points](#9-integration-points)
10. [Timeline & Effort Estimation](#10-timeline--effort-estimation)
11. [Risks & Mitigation](#11-risks--mitigation)

---

## 1. API Specification

### 1.1 Endpoint: Get My Tasks

**Purpose**: Retrieve all tasks assigned to the authenticated user

```
GET /workflows/tasks/my-tasks
```

#### Request Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| page | Integer | No | 0 | Page number (zero-based) |
| size | Integer | No | 20 | Page size (max 100) |
| sort | String | No | createTime,desc | Sort field and direction (e.g., "name,asc", "dueDate,desc") |
| status | String | No | null | Filter by task status (ACTIVE, SUSPENDED) |
| search | String | No | null | Search by task name or description (case-insensitive) |
| priority | Integer | No | null | Filter by priority (0-100) |
| processDefinitionKey | String | No | null | Filter by process definition key |
| dueBefore | ISO8601 | No | null | Filter tasks due before this date |
| dueAfter | ISO8601 | No | null | Filter tasks due after this date |

#### Response: 200 OK

```json
{
  "content": [
    {
      "id": "task-uuid-123",
      "name": "Review Leave Request",
      "description": "Review and approve leave request for employee",
      "processInstanceId": "process-instance-uuid",
      "processDefinitionId": "leave-request-process:1:def-uuid",
      "processDefinitionKey": "leave-request-process",
      "processDefinitionName": "Leave Request Process",
      "taskDefinitionKey": "reviewTask",
      "assignee": "john.doe",
      "owner": null,
      "priority": 50,
      "createTime": "2025-11-25T10:00:00Z",
      "dueDate": "2025-11-30T17:00:00Z",
      "claimTime": "2025-11-25T10:15:00Z",
      "suspended": false,
      "formKey": "leave-request-form",
      "category": "HR",
      "tenantId": "default",
      "candidateGroups": ["HR_STAFF", "HR_ADMIN"],
      "variables": {
        "employeeName": "Jane Smith",
        "requestType": "Annual Leave",
        "days": 5
      }
    }
  ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 45,
    "totalPages": 3
  },
  "links": {
    "self": "/workflows/tasks/my-tasks?page=0&size=20",
    "first": "/workflows/tasks/my-tasks?page=0&size=20",
    "next": "/workflows/tasks/my-tasks?page=1&size=20",
    "last": "/workflows/tasks/my-tasks?page=2&size=20"
  }
}
```

#### Error Responses

- **401 Unauthorized**: Missing or invalid JWT token
- **400 Bad Request**: Invalid query parameters
- **500 Internal Server Error**: Unexpected error

---

### 1.2 Endpoint: Get Group Tasks

**Purpose**: Retrieve all tasks available to user's team/group (candidate tasks)

```
GET /workflows/tasks/group-tasks
```

#### Request Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| page | Integer | No | 0 | Page number (zero-based) |
| size | Integer | No | 20 | Page size (max 100) |
| sort | String | No | createTime,desc | Sort field and direction |
| status | String | No | null | Filter by task status (ACTIVE, SUSPENDED) |
| search | String | No | null | Search by task name or description |
| priority | Integer | No | null | Filter by priority |
| processDefinitionKey | String | No | null | Filter by process definition key |
| groupId | String | No | null | Filter by specific group (must be user's group) |
| includeAssigned | Boolean | No | false | Include already assigned tasks |
| dueBefore | ISO8601 | No | null | Filter tasks due before this date |
| dueAfter | ISO8601 | No | null | Filter tasks due after this date |

#### Response: 200 OK

Same structure as My Tasks endpoint, with additional fields:

```json
{
  "content": [
    {
      "id": "task-uuid-456",
      "name": "Approve Budget Request",
      "description": "Review and approve budget allocation",
      "processInstanceId": "process-instance-uuid-2",
      "processDefinitionId": "budget-approval:2:def-uuid-2",
      "processDefinitionKey": "budget-approval",
      "processDefinitionName": "Budget Approval Process",
      "taskDefinitionKey": "approveTask",
      "assignee": null,
      "owner": "finance.manager",
      "priority": 75,
      "createTime": "2025-11-25T09:00:00Z",
      "dueDate": "2025-11-28T17:00:00Z",
      "claimTime": null,
      "suspended": false,
      "formKey": "budget-approval-form",
      "category": "FINANCE",
      "tenantId": "default",
      "candidateGroups": ["FINANCE_STAFF", "FINANCE_ADMIN"],
      "candidateUsers": ["finance.manager", "cfo"],
      "variables": {
        "requestAmount": 50000,
        "department": "IT",
        "requestor": "John Manager"
      }
    }
  ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 12,
    "totalPages": 1
  },
  "links": {
    "self": "/workflows/tasks/group-tasks?page=0&size=20",
    "first": "/workflows/tasks/group-tasks?page=0&size=20",
    "last": "/workflows/tasks/group-tasks?page=0&size=20"
  }
}
```

---

## 2. Data Models & DTOs

### 2.1 Enhanced TaskResponse DTO

Extend existing `TaskResponse` to include additional fields needed for task lists:

**File**: `/services/engine/src/main/java/com/werkflow/engine/dto/TaskResponse.java`

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponse {

    // Existing fields
    private String id;
    private String name;
    private String description;
    private String processInstanceId;
    private String processDefinitionId;
    private String taskDefinitionKey;
    private String assignee;
    private String owner;
    private Integer priority;
    private Instant createTime;
    private Instant dueDate;
    private Instant claimTime;
    private boolean suspended;
    private String formKey;
    private String category;
    private String tenantId;
    private Map<String, Object> variables;

    // NEW FIELDS for enhanced task lists
    private String processDefinitionKey;
    private String processDefinitionName;
    private List<String> candidateGroups;
    private List<String> candidateUsers;
    private Long executionDuration; // milliseconds since creation
}
```

### 2.2 TaskListResponse (Paginated Wrapper)

**File**: `/services/engine/src/main/java/com/werkflow/engine/dto/TaskListResponse.java`

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskListResponse {

    private List<TaskResponse> content;
    private PageInfo page;
    private Map<String, String> links;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageInfo {
        private int size;
        private int number;
        private long totalElements;
        private int totalPages;
    }
}
```

### 2.3 TaskQueryParams (Request Wrapper)

**File**: `/services/engine/src/main/java/com/werkflow/engine/dto/TaskQueryParams.java`

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskQueryParams {

    @Min(0)
    private Integer page = 0;

    @Min(1)
    @Max(100)
    private Integer size = 20;

    private String sort = "createTime,desc";

    private TaskStatus status;

    @Size(max = 255)
    private String search;

    @Min(0)
    @Max(100)
    private Integer priority;

    private String processDefinitionKey;

    private String groupId;

    private Boolean includeAssigned = false;

    private Instant dueBefore;

    private Instant dueAfter;

    public enum TaskStatus {
        ACTIVE, SUSPENDED
    }
}
```

### 2.4 JwtUserContext (User Claims)

**File**: `/services/engine/src/main/java/com/werkflow/engine/dto/JwtUserContext.java`

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JwtUserContext {

    private String userId;           // preferred_username
    private String email;            // email
    private String fullName;         // name
    private String department;       // department claim
    private List<String> groups;     // groups claim
    private List<String> roles;      // realm_access.roles
    private String managerId;        // manager_id claim (for delegation)
    private Integer doaLevel;        // doa_level claim

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean isInGroup(String group) {
        return groups != null && groups.contains(group);
    }
}
```

### 2.5 ErrorResponse DTO

**File**: `/services/engine/src/main/java/com/werkflow/engine/dto/ErrorResponse.java`

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    private String error;
    private String message;
    private String path;
    private Integer status;

    @Builder.Default
    private OffsetDateTime timestamp = OffsetDateTime.now();

    private Map<String, String> validationErrors;
}
```

---

## 3. Architecture & Design

### 3.1 Component Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    CLIENT (Frontend/API)                        │
│                 Authorization: Bearer <JWT>                     │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                     SecurityFilterChain                         │
│                   (Spring Security OAuth2)                      │
│  - Validates JWT signature & expiry                             │
│  - Extracts roles from realm_access                             │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│              WorkflowTaskController                             │
│  @RequestMapping("/workflows/tasks")                            │
│                                                                  │
│  + getMyTasks(@AuthenticationPrincipal Jwt, TaskQueryParams)    │
│  + getGroupTasks(@AuthenticationPrincipal Jwt, TaskQueryParams) │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                  JwtClaimsExtractor                             │
│  - extractUserContext(Jwt) -> JwtUserContext                    │
│  - getUserId(Jwt)                                               │
│  - getUserGroups(Jwt)                                           │
│  - getDepartment(Jwt)                                           │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                WorkflowTaskService                              │
│                                                                  │
│  + getMyTasks(JwtUserContext, TaskQueryParams)                  │
│  + getGroupTasks(JwtUserContext, TaskQueryParams)               │
│  - buildTaskQuery(...)                                          │
│  - applyFilters(...)                                            │
│  - applySorting(...)                                            │
│  - mapToResponse(Task)                                          │
│  - buildPaginationLinks(...)                                    │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│          org.flowable.engine.TaskService                        │
│                  (Flowable API)                                 │
│                                                                  │
│  + createTaskQuery()                                            │
│  + taskAssignee(userId)                                         │
│  + taskCandidateGroupIn(groups)                                 │
│  + taskCandidateOrAssigned(userId, groups)                      │
│  + orderBy...()                                                 │
│  + listPage(offset, limit)                                      │
│  + count()                                                      │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                   PostgreSQL Database                           │
│                  (Flowable ACT_RU_TASK)                         │
│                                                                  │
│  - ACT_RU_TASK: Active runtime tasks                            │
│  - ACT_RU_IDENTITYLINK: Task-user/group relationships           │
│  - ACT_RE_PROCDEF: Process definitions                          │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 Service Layer Design

#### WorkflowTaskService

**Responsibilities**:
1. Orchestrate task queries using Flowable TaskService
2. Apply authorization filters based on JWT claims
3. Apply pagination, sorting, and search filters
4. Transform Flowable Task entities to TaskResponse DTOs
5. Build HATEOAS pagination links
6. Handle exceptions and error responses

**Key Methods**:

```java
public TaskListResponse getMyTasks(JwtUserContext userContext, TaskQueryParams params) {
    // 1. Create base query for user-assigned tasks
    TaskQuery query = flowableTaskService.createTaskQuery()
        .taskAssignee(userContext.getUserId())
        .active();

    // 2. Apply filters (search, status, process definition, etc.)
    query = applyFilters(query, params);

    // 3. Apply sorting
    query = applySorting(query, params);

    // 4. Execute paginated query
    long totalCount = query.count();
    int offset = params.getPage() * params.getSize();
    List<Task> tasks = query.listPage(offset, params.getSize());

    // 5. Transform to DTOs
    List<TaskResponse> responses = tasks.stream()
        .map(this::mapToEnhancedResponse)
        .collect(Collectors.toList());

    // 6. Build response with pagination
    return buildTaskListResponse(responses, params, totalCount);
}
```

---

## 4. Database Query Strategy

### 4.1 Flowable Database Schema

Flowable stores task data in the following tables:

```sql
-- Active runtime tasks
ACT_RU_TASK (
    ID_ varchar(64) PRIMARY KEY,
    NAME_ varchar(255),
    DESCRIPTION_ varchar(4000),
    PRIORITY_ integer,
    ASSIGNEE_ varchar(255),        -- User ID (taskAssignee)
    OWNER_ varchar(255),
    CREATE_TIME_ timestamp,
    DUE_DATE_ timestamp,
    CLAIM_TIME_ timestamp,
    SUSPENSION_STATE_ integer,
    PROC_INST_ID_ varchar(64),
    PROC_DEF_ID_ varchar(64),
    TASK_DEF_KEY_ varchar(255),
    FORM_KEY_ varchar(255),
    CATEGORY_ varchar(255),
    TENANT_ID_ varchar(255)
)

-- Task-user/group relationships
ACT_RU_IDENTITYLINK (
    ID_ varchar(64) PRIMARY KEY,
    TYPE_ varchar(255),           -- 'candidate' for groups
    USER_ID_ varchar(255),        -- Candidate user
    GROUP_ID_ varchar(255),       -- Candidate group
    TASK_ID_ varchar(64),
    PROC_INST_ID_ varchar(64),
    PROC_DEF_ID_ varchar(64)
)

-- Process definitions
ACT_RE_PROCDEF (
    ID_ varchar(64) PRIMARY KEY,
    KEY_ varchar(255),
    NAME_ varchar(255),
    VERSION_ integer,
    CATEGORY_ varchar(255),
    DEPLOYMENT_ID_ varchar(64),
    TENANT_ID_ varchar(255)
)
```

### 4.2 Query Strategies

#### My Tasks Query

```java
// Flowable TaskQuery
TaskQuery query = flowableTaskService.createTaskQuery()
    .taskAssignee(userId)          // WHERE ASSIGNEE_ = ?
    .active()                      // AND SUSPENSION_STATE_ = 1
    .orderByTaskCreateTime()       // ORDER BY CREATE_TIME_ DESC
    .desc();

// Generated SQL (simplified)
SELECT t.*
FROM ACT_RU_TASK t
WHERE t.ASSIGNEE_ = 'john.doe'
  AND t.SUSPENSION_STATE_ = 1
ORDER BY t.CREATE_TIME_ DESC
LIMIT 20 OFFSET 0;
```

#### Group Tasks Query

```java
// Option 1: Unassigned candidate tasks only
TaskQuery query = flowableTaskService.createTaskQuery()
    .taskCandidateGroupIn(userGroups)  // User's groups from JWT
    .taskUnassigned()                  // ASSIGNEE_ IS NULL
    .active();

// Option 2: Include assigned tasks (for visibility)
TaskQuery query = flowableTaskService.createTaskQuery()
    .taskCandidateGroupIn(userGroups)
    .active();

// Generated SQL (simplified)
SELECT DISTINCT t.*
FROM ACT_RU_TASK t
INNER JOIN ACT_RU_IDENTITYLINK il ON t.ID_ = il.TASK_ID_
WHERE il.GROUP_ID_ IN ('HR_STAFF', 'HR_ADMIN', 'HR_MANAGER')
  AND il.TYPE_ = 'candidate'
  AND t.ASSIGNEE_ IS NULL            -- Optional: unassigned only
  AND t.SUSPENSION_STATE_ = 1
ORDER BY t.CREATE_TIME_ DESC
LIMIT 20 OFFSET 0;
```

### 4.3 Performance Considerations

1. **Indexes**: Flowable creates indexes on:
   - `ASSIGNEE_` (for my-tasks query)
   - `PROC_INST_ID_` (for process instance lookup)
   - `TASK_DEF_KEY_` (for filtering by task type)

2. **Pagination**: Use `listPage(offset, limit)` to avoid loading all results

3. **Count Queries**: Flowable executes separate `COUNT(*)` query for pagination

4. **Process Definition Join**: Cached in Flowable's process definition cache

---

## 5. Authorization & Security

### 5.1 Security Model

```
┌─────────────────────────────────────────────────────────────────┐
│                       AUTHORIZATION LAYERS                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. AUTHENTICATION (Spring Security)                            │
│     - Validate JWT signature                                    │
│     - Check token expiry                                        │
│     - Verify issuer (Keycloak)                                  │
│                                                                  │
│  2. ENDPOINT AUTHORIZATION (SecurityConfig)                     │
│     - /workflows/tasks/** requires authenticated()              │
│     - No specific role required (handled by task ownership)     │
│                                                                  │
│  3. TASK OWNERSHIP AUTHORIZATION (WorkflowTaskService)          │
│     - My Tasks: userId matches task assignee                    │
│     - Group Tasks: user groups match task candidate groups      │
│                                                                  │
│  4. DATA FILTERING (Flowable Queries)                           │
│     - Query filters automatically enforce ownership             │
│     - No additional authorization checks needed                 │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 5.2 JWT Claims Extraction

**File**: `/services/engine/src/main/java/com/werkflow/engine/util/JwtClaimsExtractor.java`

```java
@Component
@Slf4j
public class JwtClaimsExtractor {

    /**
     * Extract complete user context from JWT token
     */
    public JwtUserContext extractUserContext(Jwt jwt) {
        return JwtUserContext.builder()
            .userId(getUserId(jwt))
            .email(getEmail(jwt))
            .fullName(getFullName(jwt))
            .department(getDepartment(jwt))
            .groups(getUserGroups(jwt))
            .roles(getUserRoles(jwt))
            .managerId(getManagerId(jwt))
            .doaLevel(getDoaLevel(jwt))
            .build();
    }

    /**
     * Get user ID (preferred_username)
     */
    public String getUserId(Jwt jwt) {
        return jwt.getClaimAsString("preferred_username");
    }

    /**
     * Get user groups from JWT claims
     * Returns list of group IDs user belongs to
     */
    public List<String> getUserGroups(Jwt jwt) {
        Object groupsClaim = jwt.getClaim("groups");

        if (groupsClaim instanceof List) {
            return ((List<?>) groupsClaim).stream()
                .map(Object::toString)
                .collect(Collectors.toList());
        }

        log.warn("User {} has no groups claim", getUserId(jwt));
        return Collections.emptyList();
    }

    /**
     * Get department from JWT claims
     */
    public String getDepartment(Jwt jwt) {
        return jwt.getClaimAsString("department");
    }

    /**
     * Get user roles from realm_access claim
     */
    public List<String> getUserRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");

        if (realmAccess != null && realmAccess.containsKey("roles")) {
            Object rolesObj = realmAccess.get("roles");
            if (rolesObj instanceof List) {
                return ((List<?>) rolesObj).stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }

    // Additional methods for email, name, managerId, doaLevel...
}
```

### 5.3 Authorization Logic

```java
// My Tasks - Authorization enforced by query
// User can only see tasks assigned to them
TaskQuery query = flowableTaskService.createTaskQuery()
    .taskAssignee(userContext.getUserId());  // Enforces ownership

// Group Tasks - Authorization enforced by query
// User can only see tasks for groups they belong to
TaskQuery query = flowableTaskService.createTaskQuery()
    .taskCandidateGroupIn(userContext.getGroups());  // Enforces group membership

// Additional validation (optional)
if (params.getGroupId() != null) {
    // Ensure user is in the requested group
    if (!userContext.isInGroup(params.getGroupId())) {
        throw new AccessDeniedException(
            "User is not a member of group: " + params.getGroupId()
        );
    }
}
```

---

## 6. Implementation Plan

### Phase 1: Core Infrastructure (Day 1 - 4 hours)

**File Structure**:
```
services/engine/src/main/java/com/werkflow/engine/
├── controller/
│   └── WorkflowTaskController.java          [NEW]
├── service/
│   ├── TaskService.java                     [EXISTING - NO CHANGES]
│   └── WorkflowTaskService.java             [NEW]
├── dto/
│   ├── TaskResponse.java                    [EXISTING - ENHANCE]
│   ├── TaskListResponse.java                [NEW]
│   ├── TaskQueryParams.java                 [NEW]
│   ├── JwtUserContext.java                  [NEW]
│   └── ErrorResponse.java                   [NEW]
├── util/
│   └── JwtClaimsExtractor.java              [NEW]
├── exception/
│   ├── TaskNotFoundException.java           [NEW]
│   ├── UnauthorizedTaskAccessException.java [NEW]
│   └── GlobalExceptionHandler.java          [NEW]
└── config/
    └── SecurityConfig.java                  [EXISTING - UPDATE]
```

**Tasks**:
1. Create new DTOs (TaskListResponse, TaskQueryParams, JwtUserContext, ErrorResponse)
2. Enhance TaskResponse with new fields
3. Create JwtClaimsExtractor utility class
4. Create custom exception classes
5. Create GlobalExceptionHandler

**Estimated Time**: 4 hours

---

### Phase 2: Service Layer (Day 1-2 - 6 hours)

**File**: `WorkflowTaskService.java`

**Implementation Steps**:

1. **Dependency Injection**:
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowTaskService {

    private final org.flowable.engine.TaskService flowableTaskService;
    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    // Cache for process definition names (30s TTL)
    @Cacheable(value = "processDefinitionNames", key = "#processDefinitionId")
    public String getProcessDefinitionName(String processDefinitionId) {
        ProcessDefinition procDef = repositoryService
            .createProcessDefinitionQuery()
            .processDefinitionId(processDefinitionId)
            .singleResult();
        return procDef != null ? procDef.getName() : null;
    }
}
```

2. **My Tasks Method**:
```java
public TaskListResponse getMyTasks(JwtUserContext userContext, TaskQueryParams params) {
    log.debug("Fetching my tasks for user: {}", userContext.getUserId());

    // 1. Build base query
    TaskQuery query = flowableTaskService.createTaskQuery()
        .taskAssignee(userContext.getUserId())
        .active();

    // 2. Apply filters
    query = applyFilters(query, params);

    // 3. Apply sorting
    query = applySorting(query, params);

    // 4. Execute query with pagination
    long totalCount = query.count();
    int offset = params.getPage() * params.getSize();
    List<Task> tasks = query.listPage(offset, params.getSize());

    log.info("Found {} my tasks (total: {}) for user: {}",
        tasks.size(), totalCount, userContext.getUserId());

    // 5. Transform to DTOs
    List<TaskResponse> responses = tasks.stream()
        .map(this::mapToEnhancedResponse)
        .collect(Collectors.toList());

    // 6. Build paginated response
    return buildTaskListResponse(responses, params, totalCount, "/workflows/tasks/my-tasks");
}
```

3. **Group Tasks Method**:
```java
public TaskListResponse getGroupTasks(JwtUserContext userContext, TaskQueryParams params) {
    log.debug("Fetching group tasks for user: {} (groups: {})",
        userContext.getUserId(), userContext.getGroups());

    // Validate user has groups
    if (userContext.getGroups() == null || userContext.getGroups().isEmpty()) {
        log.warn("User {} has no groups, returning empty task list", userContext.getUserId());
        return buildEmptyTaskListResponse();
    }

    // 1. Build base query
    TaskQuery query = flowableTaskService.createTaskQuery()
        .taskCandidateGroupIn(userContext.getGroups())
        .active();

    // 2. Filter by specific group if requested
    if (params.getGroupId() != null) {
        if (!userContext.isInGroup(params.getGroupId())) {
            throw new UnauthorizedTaskAccessException(
                "User is not a member of group: " + params.getGroupId()
            );
        }
        query = flowableTaskService.createTaskQuery()
            .taskCandidateGroup(params.getGroupId())
            .active();
    }

    // 3. Optionally exclude assigned tasks
    if (!params.getIncludeAssigned()) {
        query.taskUnassigned();
    }

    // 4-6. Same as my-tasks...
}
```

4. **Helper Methods**:
```java
private TaskQuery applyFilters(TaskQuery query, TaskQueryParams params) {
    // Search filter
    if (params.getSearch() != null && !params.getSearch().isBlank()) {
        query.taskNameLikeIgnoreCase("%" + params.getSearch() + "%");
    }

    // Priority filter
    if (params.getPriority() != null) {
        query.taskPriority(params.getPriority());
    }

    // Process definition filter
    if (params.getProcessDefinitionKey() != null) {
        query.processDefinitionKey(params.getProcessDefinitionKey());
    }

    // Due date filters
    if (params.getDueBefore() != null) {
        query.taskDueBefore(Date.from(params.getDueBefore()));
    }
    if (params.getDueAfter() != null) {
        query.taskDueAfter(Date.from(params.getDueAfter()));
    }

    // Status filter
    if (params.getStatus() == TaskQueryParams.TaskStatus.SUSPENDED) {
        query.suspended();
    }

    return query;
}

private TaskQuery applySorting(TaskQuery query, TaskQueryParams params) {
    String[] sortParts = params.getSort().split(",");
    String sortField = sortParts[0];
    boolean ascending = sortParts.length > 1 && "asc".equalsIgnoreCase(sortParts[1]);

    switch (sortField) {
        case "name":
            query.orderByTaskName();
            break;
        case "priority":
            query.orderByTaskPriority();
            break;
        case "dueDate":
            query.orderByDueDate();
            break;
        case "createTime":
        default:
            query.orderByTaskCreateTime();
    }

    if (ascending) {
        query.asc();
    } else {
        query.desc();
    }

    return query;
}

private TaskResponse mapToEnhancedResponse(Task task) {
    // Get process definition details
    String processDefinitionName = getProcessDefinitionName(task.getProcessDefinitionId());

    // Get candidate groups (from identity links)
    List<String> candidateGroups = flowableTaskService.getIdentityLinksForTask(task.getId())
        .stream()
        .filter(link -> "candidate".equals(link.getType()) && link.getGroupId() != null)
        .map(IdentityLink::getGroupId)
        .collect(Collectors.toList());

    // Get candidate users
    List<String> candidateUsers = flowableTaskService.getIdentityLinksForTask(task.getId())
        .stream()
        .filter(link -> "candidate".equals(link.getType()) && link.getUserId() != null)
        .map(IdentityLink::getUserId)
        .collect(Collectors.toList());

    // Get task variables
    Map<String, Object> variables = flowableTaskService.getVariables(task.getId());

    // Calculate execution duration
    long executionDuration = task.getCreateTime() != null
        ? System.currentTimeMillis() - task.getCreateTime().getTime()
        : 0L;

    return TaskResponse.builder()
        .id(task.getId())
        .name(task.getName())
        .description(task.getDescription())
        .processInstanceId(task.getProcessInstanceId())
        .processDefinitionId(task.getProcessDefinitionId())
        .processDefinitionKey(getProcessDefinitionKey(task.getProcessDefinitionId()))
        .processDefinitionName(processDefinitionName)
        .taskDefinitionKey(task.getTaskDefinitionKey())
        .assignee(task.getAssignee())
        .owner(task.getOwner())
        .priority(task.getPriority())
        .createTime(toInstant(task.getCreateTime()))
        .dueDate(toInstant(task.getDueDate()))
        .claimTime(toInstant(task.getClaimTime()))
        .suspended(task.isSuspended())
        .formKey(task.getFormKey())
        .category(task.getCategory())
        .tenantId(task.getTenantId())
        .candidateGroups(candidateGroups)
        .candidateUsers(candidateUsers)
        .executionDuration(executionDuration)
        .variables(variables)
        .build();
}

private TaskListResponse buildTaskListResponse(
        List<TaskResponse> tasks,
        TaskQueryParams params,
        long totalCount,
        String basePath) {

    int totalPages = (int) Math.ceil((double) totalCount / params.getSize());

    TaskListResponse.PageInfo pageInfo = TaskListResponse.PageInfo.builder()
        .size(params.getSize())
        .number(params.getPage())
        .totalElements(totalCount)
        .totalPages(totalPages)
        .build();

    Map<String, String> links = buildPaginationLinks(basePath, params, totalPages);

    return TaskListResponse.builder()
        .content(tasks)
        .page(pageInfo)
        .links(links)
        .build();
}

private Map<String, String> buildPaginationLinks(String basePath, TaskQueryParams params, int totalPages) {
    Map<String, String> links = new HashMap<>();

    String baseUrl = basePath + "?size=" + params.getSize();

    links.put("self", baseUrl + "&page=" + params.getPage());
    links.put("first", baseUrl + "&page=0");

    if (params.getPage() > 0) {
        links.put("prev", baseUrl + "&page=" + (params.getPage() - 1));
    }

    if (params.getPage() < totalPages - 1) {
        links.put("next", baseUrl + "&page=" + (params.getPage() + 1));
    }

    links.put("last", baseUrl + "&page=" + (totalPages - 1));

    return links;
}
```

**Estimated Time**: 6 hours

---

### Phase 3: Controller Layer (Day 2 - 3 hours)

**File**: `WorkflowTaskController.java`

```java
@RestController
@RequestMapping("/workflows/tasks")
@RequiredArgsConstructor
@Tag(name = "Workflow Tasks", description = "User task management for workflows")
@SecurityRequirement(name = "bearer-jwt")
@Slf4j
public class WorkflowTaskController {

    private final WorkflowTaskService workflowTaskService;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    @GetMapping("/my-tasks")
    @Operation(
        summary = "Get my tasks",
        description = "Retrieve all tasks assigned to the authenticated user with pagination, filtering, and sorting"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Tasks retrieved successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = TaskListResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Invalid or missing JWT token"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Bad Request - Invalid query parameters"
        )
    })
    public ResponseEntity<TaskListResponse> getMyTasks(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute TaskQueryParams params) {

        log.info("GET /workflows/tasks/my-tasks - User: {}", jwt.getClaimAsString("preferred_username"));

        // Extract user context from JWT
        JwtUserContext userContext = jwtClaimsExtractor.extractUserContext(jwt);

        // Fetch tasks
        TaskListResponse response = workflowTaskService.getMyTasks(userContext, params);

        log.info("Returning {} tasks (page {}/{}) for user: {}",
            response.getContent().size(),
            response.getPage().getNumber() + 1,
            response.getPage().getTotalPages(),
            userContext.getUserId());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/group-tasks")
    @Operation(
        summary = "Get group tasks",
        description = "Retrieve all tasks available to the authenticated user's groups (candidate tasks)"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Tasks retrieved successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = TaskListResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Invalid or missing JWT token"
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - User not in requested group"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Bad Request - Invalid query parameters"
        )
    })
    public ResponseEntity<TaskListResponse> getGroupTasks(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute TaskQueryParams params) {

        log.info("GET /workflows/tasks/group-tasks - User: {}", jwt.getClaimAsString("preferred_username"));

        // Extract user context from JWT
        JwtUserContext userContext = jwtClaimsExtractor.extractUserContext(jwt);

        // Fetch group tasks
        TaskListResponse response = workflowTaskService.getGroupTasks(userContext, params);

        log.info("Returning {} group tasks (page {}/{}) for user: {} (groups: {})",
            response.getContent().size(),
            response.getPage().getNumber() + 1,
            response.getPage().getTotalPages(),
            userContext.getUserId(),
            userContext.getGroups());

        return ResponseEntity.ok(response);
    }
}
```

**Estimated Time**: 3 hours

---

### Phase 4: Exception Handling (Day 2 - 2 hours)

**Files**:

1. **TaskNotFoundException.java**:
```java
public class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException(String taskId) {
        super("Task not found with ID: " + taskId);
    }
}
```

2. **UnauthorizedTaskAccessException.java**:
```java
public class UnauthorizedTaskAccessException extends RuntimeException {
    public UnauthorizedTaskAccessException(String message) {
        super(message);
    }
}
```

3. **GlobalExceptionHandler.java**:
```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTaskNotFoundException(
            TaskNotFoundException ex, WebRequest request) {
        log.error("Task not found: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
            .timestamp(OffsetDateTime.now())
            .status(HttpStatus.NOT_FOUND.value())
            .error(HttpStatus.NOT_FOUND.getReasonPhrase())
            .message(ex.getMessage())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(UnauthorizedTaskAccessException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedTaskAccessException(
            UnauthorizedTaskAccessException ex, WebRequest request) {
        log.error("Unauthorized task access: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
            .timestamp(OffsetDateTime.now())
            .status(HttpStatus.FORBIDDEN.value())
            .error(HttpStatus.FORBIDDEN.getReasonPhrase())
            .message(ex.getMessage())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    // Additional handlers for validation errors, IllegalArgumentException, etc.
}
```

**Estimated Time**: 2 hours

---

### Phase 5: Configuration Updates (Day 2 - 1 hour)

**File**: `SecurityConfig.java`

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            // ... existing configurations ...

            // NEW: Workflow task endpoints - authenticated users
            .requestMatchers(new AntPathRequestMatcher("/workflows/tasks/**")).authenticated()

            // All other requests require authentication
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
        );

    return http.build();
}
```

**Estimated Time**: 1 hour

---

### Phase 6: Caching Configuration (Day 3 - 1 hour)

**File**: Update `CacheConfig.java`

```java
@Bean
public CacheManager cacheManager() {
    CaffeineCacheManager cacheManager = new CaffeineCacheManager(
        "serviceUrls",               // Existing
        "processDefinitionNames"     // NEW: Cache process definition names
    );

    cacheManager.setCaffeine(Caffeine.newBuilder()
        .expireAfterWrite(30, TimeUnit.SECONDS)
        .maximumSize(100)
        .recordStats());

    return cacheManager;
}
```

**Estimated Time**: 1 hour

---

## 7. Testing Strategy

### 7.1 Unit Tests

**File**: `WorkflowTaskServiceTest.java`

```java
@ExtendWith(MockitoExtension.class)
class WorkflowTaskServiceTest {

    @Mock
    private org.flowable.engine.TaskService flowableTaskService;

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private JwtClaimsExtractor jwtClaimsExtractor;

    @InjectMocks
    private WorkflowTaskService workflowTaskService;

    @Test
    void getMyTasks_shouldReturnUserAssignedTasks() {
        // Arrange
        JwtUserContext userContext = createTestUserContext("john.doe");
        TaskQueryParams params = createDefaultParams();

        TaskQuery mockQuery = mock(TaskQuery.class);
        when(flowableTaskService.createTaskQuery()).thenReturn(mockQuery);
        when(mockQuery.taskAssignee(anyString())).thenReturn(mockQuery);
        when(mockQuery.active()).thenReturn(mockQuery);
        when(mockQuery.count()).thenReturn(5L);
        when(mockQuery.listPage(anyInt(), anyInt())).thenReturn(createMockTasks(5));

        // Act
        TaskListResponse response = workflowTaskService.getMyTasks(userContext, params);

        // Assert
        assertNotNull(response);
        assertEquals(5, response.getContent().size());
        assertEquals(0, response.getPage().getNumber());
        assertEquals(20, response.getPage().getSize());
        assertEquals(5, response.getPage().getTotalElements());

        verify(mockQuery).taskAssignee("john.doe");
        verify(mockQuery).active();
    }

    @Test
    void getGroupTasks_shouldReturnCandidateTasksForUserGroups() {
        // Similar test structure...
    }

    @Test
    void getGroupTasks_shouldThrowExceptionWhenUserNotInGroup() {
        // Test authorization validation...
    }
}
```

### 7.2 Integration Tests

**File**: `WorkflowTaskControllerIntegrationTest.java`

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowTaskControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private ProcessEngine processEngine;

    @Test
    void getMyTasks_shouldReturn200WithTaskList() throws Exception {
        // Arrange: Create test JWT
        Jwt jwt = createMockJwt("john.doe", List.of("HR_STAFF"));
        when(jwtDecoder.decode(anyString())).thenReturn(jwt);

        // Arrange: Start process and create task
        startTestProcessWithTask("john.doe");

        // Act & Assert
        mockMvc.perform(get("/workflows/tasks/my-tasks")
                .header("Authorization", "Bearer mock-token")
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(jsonPath("$.links.self").exists());
    }

    @Test
    void getGroupTasks_shouldReturn200WithCandidateTasks() throws Exception {
        // Similar structure...
    }

    @Test
    void getMyTasks_shouldReturn401WhenNoToken() throws Exception {
        mockMvc.perform(get("/workflows/tasks/my-tasks"))
            .andExpect(status().isUnauthorized());
    }
}
```

### 7.3 Test Coverage Goals

- **Unit Tests**: > 80% line coverage
- **Integration Tests**: All endpoints with positive and negative scenarios
- **Performance Tests**: Response times < 500ms with 100 tasks

**Test Scenarios**:

1. Authentication & Authorization
   - Valid JWT token
   - Missing JWT token (401)
   - Expired JWT token (401)
   - User not in requested group (403)

2. Pagination
   - First page
   - Middle page
   - Last page
   - Page out of bounds

3. Filtering
   - Search by task name
   - Filter by status
   - Filter by priority
   - Filter by process definition
   - Filter by due date range

4. Sorting
   - Sort by name (asc/desc)
   - Sort by priority (asc/desc)
   - Sort by create time (asc/desc)
   - Sort by due date (asc/desc)

5. Edge Cases
   - User with no tasks
   - User with no groups
   - Invalid query parameters
   - Large page size (>100)
   - Negative page number

**Estimated Testing Time**: 6 hours

---

## 8. Performance Optimization

### 8.1 Caching Strategy

```
┌────────────────────────────────────────────────────────────┐
│                      CACHING LAYERS                        │
├────────────────────────────────────────────────────────────┤
│                                                             │
│  1. Process Definition Name Cache (Caffeine, 30s TTL)     │
│     - Key: processDefinitionId                             │
│     - Value: processDefinitionName                         │
│     - Max Size: 100 entries                                │
│     - Hit Rate: ~95% (definitions rarely change)           │
│                                                             │
│  2. Flowable Internal Caches                               │
│     - Process Definition Cache (in-memory)                 │
│     - Deployment Cache (in-memory)                         │
│                                                             │
│  3. Database Connection Pool (HikariCP)                    │
│     - Max Pool Size: 10                                    │
│     - Min Idle: 2                                          │
│     - Connection Timeout: 30s                              │
│                                                             │
└────────────────────────────────────────────────────────────┘
```

### 8.2 Query Optimization

**Optimizations Applied**:

1. **Lazy Loading**: Only load task variables when needed
2. **Batch Fetching**: Fetch candidate groups in single query per task
3. **Pagination**: Always use `listPage(offset, limit)` to avoid loading all results
4. **Index Usage**: Leverage Flowable's built-in indexes on ASSIGNEE_, GROUP_ID_

**Query Performance Benchmarks**:

| Scenario | Task Count | Expected Response Time |
|----------|------------|------------------------|
| My Tasks (assigned) | 10 | < 100ms |
| My Tasks (assigned) | 100 | < 200ms |
| My Tasks (assigned) | 1000 | < 300ms |
| Group Tasks (unassigned) | 10 | < 150ms |
| Group Tasks (unassigned) | 100 | < 250ms |
| Group Tasks (unassigned) | 1000 | < 400ms |

### 8.3 Response Size Optimization

**Techniques**:

1. **Pagination**: Limit default page size to 20 (max 100)
2. **Selective Variable Loading**: Only load variables for displayed tasks
3. **GZIP Compression**: Enable in Spring Boot (already configured)

**Expected Response Sizes**:

| Page Size | Approximate Response Size |
|-----------|---------------------------|
| 20 tasks | ~50 KB |
| 50 tasks | ~125 KB |
| 100 tasks | ~250 KB |

---

## 9. Integration Points

### 9.1 JWT Claims Extractor

**Purpose**: Extract user information from Keycloak JWT tokens

**Integration**:
```java
@Autowired
private JwtClaimsExtractor jwtClaimsExtractor;

// In controller
JwtUserContext userContext = jwtClaimsExtractor.extractUserContext(jwt);
```

**Claims Used**:
- `preferred_username` -> userId
- `groups` -> user groups
- `department` -> department
- `realm_access.roles` -> roles
- `manager_id` -> managerId (for delegation)
- `doa_level` -> DOA level (for approvals)

### 9.2 Flowable TaskService

**Purpose**: Query and manage workflow tasks

**Key Methods Used**:
```java
// Create queries
TaskQuery createTaskQuery()

// Filtering
taskAssignee(String userId)
taskCandidateGroupIn(List<String> groups)
taskCandidateGroup(String groupId)
taskUnassigned()
active() / suspended()

// Pagination
count()
listPage(int offset, int limit)

// Sorting
orderByTaskName() / orderByTaskPriority() / orderByTaskCreateTime() / orderByDueDate()
asc() / desc()

// Identity links
getIdentityLinksForTask(String taskId)
```

### 9.3 Security Integration

**Spring Security OAuth2 Resource Server**:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/workflows/tasks/**").authenticated()
            );
    }
}
```

### 9.4 Frontend Integration

**Admin Portal** (`/frontends/admin-portal`):

**API Client** (`/lib/api/tasks.ts`):
```typescript
export interface TaskResponse {
  id: string;
  name: string;
  description?: string;
  processInstanceId: string;
  assignee?: string;
  priority: number;
  createTime: string;
  dueDate?: string;
  // ... other fields
}

export interface TaskListResponse {
  content: TaskResponse[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
  links: Record<string, string>;
}

export async function getMyTasks(params: TaskQueryParams): Promise<TaskListResponse> {
  const response = await fetch('/workflows/tasks/my-tasks?' + new URLSearchParams(params), {
    headers: {
      'Authorization': `Bearer ${getAccessToken()}`
    }
  });
  return response.json();
}

export async function getGroupTasks(params: TaskQueryParams): Promise<TaskListResponse> {
  const response = await fetch('/workflows/tasks/group-tasks?' + new URLSearchParams(params), {
    headers: {
      'Authorization': `Bearer ${getAccessToken()}`
    }
  });
  return response.json();
}
```

---

## 10. Timeline & Effort Estimation

### 10.1 Development Timeline

| Phase | Tasks | Duration | Dependencies |
|-------|-------|----------|--------------|
| **Day 1** | Infrastructure & DTOs | 4 hours | None |
| **Day 1-2** | Service Layer Implementation | 6 hours | Phase 1 complete |
| **Day 2** | Controller Layer | 3 hours | Phase 2 complete |
| **Day 2** | Exception Handling | 2 hours | Phase 3 complete |
| **Day 2** | Configuration Updates | 1 hour | Phase 4 complete |
| **Day 3** | Caching Configuration | 1 hour | Phase 5 complete |
| **Day 3** | Unit Tests | 3 hours | Phase 2-3 complete |
| **Day 3** | Integration Tests | 3 hours | Phase 6 complete |
| **Day 3** | Documentation & Swagger | 1 hour | All phases complete |

**Total Estimated Time**: 24 hours (3 days)

### 10.2 Resource Allocation

- **Backend Developer**: 1 person, full-time
- **Code Reviewer**: 1 person, 2-3 hours
- **QA/Tester**: 1 person, 4 hours (Day 3)

### 10.3 Milestones

1. **Day 1 EOD**: Core infrastructure and service layer 80% complete
2. **Day 2 EOD**: All endpoints functional, basic tests passing
3. **Day 3 EOD**: Full test coverage, documentation complete, ready for code review

---

## 11. Risks & Mitigation

### 11.1 Technical Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Flowable query performance degrades with large task count | HIGH | MEDIUM | Implement pagination limits, add database indexes, use caching |
| JWT claims extraction fails for some tokens | HIGH | LOW | Add comprehensive error handling, fallback to default values, extensive testing |
| Memory issues with large task variable maps | MEDIUM | LOW | Implement selective variable loading, limit variable size in response |
| Integration issues with existing TaskService | MEDIUM | LOW | Use separate WorkflowTaskService, don't modify existing service |
| Keycloak JWT claims structure changes | MEDIUM | LOW | Use flexible claim extraction with null checks, log warnings |

### 11.2 Implementation Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Timeline overrun due to complexity | MEDIUM | MEDIUM | Start with MVP features, add enhancements in Phase 2 |
| Breaking existing task functionality | HIGH | LOW | Create new controller/service, keep existing TaskController unchanged |
| Security vulnerabilities in authorization | HIGH | LOW | Thorough security review, use Flowable's built-in authorization |
| Incomplete test coverage | MEDIUM | MEDIUM | Set coverage goals early, write tests alongside code |

### 11.3 Integration Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Frontend API contract mismatch | MEDIUM | LOW | Share OpenAPI spec early, coordinate with frontend team |
| JWT token format incompatible | HIGH | LOW | Test with real Keycloak tokens from Day 1 |
| Performance issues in production | MEDIUM | MEDIUM | Load test with realistic data volumes, add monitoring |

### 11.4 Mitigation Strategies

1. **Phased Rollout**:
   - Deploy to dev environment first
   - Test with real workflows and users
   - Monitor performance metrics
   - Gradual rollout to staging and production

2. **Rollback Plan**:
   - No changes to existing TaskController (backward compatible)
   - Feature can be disabled via SecurityConfig
   - Database queries don't modify data (read-only)

3. **Monitoring & Alerting**:
   ```yaml
   # Prometheus metrics to monitor
   - http_server_requests_seconds{uri="/workflows/tasks/my-tasks"}
   - http_server_requests_seconds{uri="/workflows/tasks/group-tasks"}
   - cache_gets{cache="processDefinitionNames"}
   - cache_misses{cache="processDefinitionNames"}
   ```

4. **Performance Benchmarking**:
   - Run JMeter tests with 100 concurrent users
   - Test with 10, 100, 1000, 10000 tasks in database
   - Verify p95 latency < 500ms
   - Monitor memory usage during load tests

---

## Appendix A: Example Requests & Responses

### Example 1: Get My Tasks (First Page)

**Request**:
```http
GET /workflows/tasks/my-tasks?page=0&size=20&sort=createTime,desc HTTP/1.1
Host: localhost:8081
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Response**:
```json
{
  "content": [
    {
      "id": "e8c5f3d2-9b1a-4c7e-8f2d-3a5b6c7d8e9f",
      "name": "Review Leave Request",
      "description": "Review leave request for Jane Smith - 5 days",
      "processInstanceId": "a1b2c3d4-5e6f-7g8h-9i0j-1k2l3m4n5o6p",
      "processDefinitionId": "leave-request:1:def-id-123",
      "processDefinitionKey": "leave-request",
      "processDefinitionName": "Leave Request Process",
      "taskDefinitionKey": "managerReviewTask",
      "assignee": "john.doe",
      "owner": null,
      "priority": 50,
      "createTime": "2025-11-25T10:30:00Z",
      "dueDate": "2025-11-30T17:00:00Z",
      "claimTime": "2025-11-25T10:32:00Z",
      "suspended": false,
      "formKey": "leave-request-review-form",
      "category": "HR",
      "tenantId": "default",
      "candidateGroups": ["HR_MANAGER", "SUPER_ADMIN"],
      "candidateUsers": [],
      "executionDuration": 120000,
      "variables": {
        "employeeName": "Jane Smith",
        "leaveType": "Annual Leave",
        "startDate": "2025-12-01",
        "endDate": "2025-12-05",
        "days": 5,
        "reason": "Family vacation"
      }
    }
  ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 1,
    "totalPages": 1
  },
  "links": {
    "self": "/workflows/tasks/my-tasks?page=0&size=20",
    "first": "/workflows/tasks/my-tasks?page=0&size=20",
    "last": "/workflows/tasks/my-tasks?page=0&size=20"
  }
}
```

### Example 2: Get Group Tasks (Filtered)

**Request**:
```http
GET /workflows/tasks/group-tasks?page=0&size=20&search=budget&includeAssigned=false HTTP/1.1
Host: localhost:8081
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Response**:
```json
{
  "content": [
    {
      "id": "f9d6e4c3-a2b1-5d7e-9f3c-4b5a6c7d8e9f",
      "name": "Approve Budget Allocation",
      "description": "Review and approve IT department budget request",
      "processInstanceId": "b2c3d4e5-6f7g-8h9i-0j1k-2l3m4n5o6p7q",
      "processDefinitionId": "budget-approval:2:def-id-456",
      "processDefinitionKey": "budget-approval",
      "processDefinitionName": "Budget Approval Process",
      "taskDefinitionKey": "financeApprovalTask",
      "assignee": null,
      "owner": "finance.manager",
      "priority": 75,
      "createTime": "2025-11-25T09:00:00Z",
      "dueDate": "2025-11-28T17:00:00Z",
      "claimTime": null,
      "suspended": false,
      "formKey": "budget-approval-form",
      "category": "FINANCE",
      "tenantId": "default",
      "candidateGroups": ["FINANCE_STAFF", "FINANCE_ADMIN"],
      "candidateUsers": ["finance.manager", "cfo"],
      "executionDuration": 14400000,
      "variables": {
        "department": "IT",
        "amount": 50000,
        "requestedBy": "John Manager",
        "fiscalYear": 2026,
        "category": "CapEx"
      }
    }
  ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 1,
    "totalPages": 1
  },
  "links": {
    "self": "/workflows/tasks/group-tasks?page=0&size=20&search=budget&includeAssigned=false",
    "first": "/workflows/tasks/group-tasks?page=0&size=20&search=budget&includeAssigned=false",
    "last": "/workflows/tasks/group-tasks?page=0&size=20&search=budget&includeAssigned=false"
  }
}
```

---

## Appendix B: Database Indexes

Flowable automatically creates the following indexes on `ACT_RU_TASK`:

```sql
-- Primary key index
CREATE UNIQUE INDEX ACT_IDX_TASK_PKEY ON ACT_RU_TASK(ID_);

-- Task assignee index (for my-tasks query)
CREATE INDEX ACT_IDX_TASK_ASSIGNEE ON ACT_RU_TASK(ASSIGNEE_);

-- Process instance index
CREATE INDEX ACT_IDX_TASK_PROCINST ON ACT_RU_TASK(PROC_INST_ID_);

-- Execution index
CREATE INDEX ACT_IDX_TASK_EXEC ON ACT_RU_TASK(EXECUTION_ID_);

-- Process definition index
CREATE INDEX ACT_IDX_TASK_PROCDEF ON ACT_RU_TASK(PROC_DEF_ID_);
```

For `ACT_RU_IDENTITYLINK` (candidate groups):

```sql
-- Primary key index
CREATE UNIQUE INDEX ACT_IDX_IDENTITYLINK_PKEY ON ACT_RU_IDENTITYLINK(ID_);

-- Task identity link index (for group-tasks query)
CREATE INDEX ACT_IDX_IDENT_LNK_TASK ON ACT_RU_IDENTITYLINK(TASK_ID_);

-- Process instance identity link index
CREATE INDEX ACT_IDX_IDENT_LNK_PROCINST ON ACT_RU_IDENTITYLINK(PROC_INST_ID_);

-- Group identity link index
CREATE INDEX ACT_IDX_IDENT_LNK_GROUP ON ACT_RU_IDENTITYLINK(GROUP_ID_);
```

No additional indexes required - Flowable's default indexes are sufficient for optimal query performance.

---

## Appendix C: OpenAPI Specification (Swagger)

The following OpenAPI spec will be automatically generated by SpringDoc:

```yaml
openapi: 3.0.1
info:
  title: Werkflow Engine Service API
  version: 1.0.0
paths:
  /workflows/tasks/my-tasks:
    get:
      tags:
        - Workflow Tasks
      summary: Get my tasks
      description: Retrieve all tasks assigned to the authenticated user with pagination, filtering, and sorting
      operationId: getMyTasks
      parameters:
        - name: page
          in: query
          schema:
            type: integer
            default: 0
        - name: size
          in: query
          schema:
            type: integer
            default: 20
        - name: sort
          in: query
          schema:
            type: string
            default: "createTime,desc"
        - name: search
          in: query
          schema:
            type: string
        - name: priority
          in: query
          schema:
            type: integer
        - name: processDefinitionKey
          in: query
          schema:
            type: string
      responses:
        '200':
          description: Tasks retrieved successfully
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/TaskListResponse'
        '401':
          description: Unauthorized - Invalid or missing JWT token
        '400':
          description: Bad Request - Invalid query parameters
      security:
        - bearer-jwt: []

  /workflows/tasks/group-tasks:
    get:
      tags:
        - Workflow Tasks
      summary: Get group tasks
      description: Retrieve all tasks available to the authenticated user's groups
      operationId: getGroupTasks
      parameters:
        - name: page
          in: query
          schema:
            type: integer
            default: 0
        - name: size
          in: query
          schema:
            type: integer
            default: 20
        - name: groupId
          in: query
          schema:
            type: string
        - name: includeAssigned
          in: query
          schema:
            type: boolean
            default: false
      responses:
        '200':
          description: Tasks retrieved successfully
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/TaskListResponse'
        '401':
          description: Unauthorized
        '403':
          description: Forbidden - User not in requested group
      security:
        - bearer-jwt: []

components:
  schemas:
    TaskListResponse:
      type: object
      properties:
        content:
          type: array
          items:
            $ref: '#/components/schemas/TaskResponse'
        page:
          $ref: '#/components/schemas/PageInfo'
        links:
          type: object
          additionalProperties:
            type: string

    TaskResponse:
      type: object
      properties:
        id:
          type: string
        name:
          type: string
        description:
          type: string
        processInstanceId:
          type: string
        assignee:
          type: string
        priority:
          type: integer
        createTime:
          type: string
          format: date-time
        dueDate:
          type: string
          format: date-time
        # ... additional fields

    PageInfo:
      type: object
      properties:
        size:
          type: integer
        number:
          type: integer
        totalElements:
          type: integer
          format: int64
        totalPages:
          type: integer

  securitySchemes:
    bearer-jwt:
      type: http
      scheme: bearer
      bearerFormat: JWT
```

---

## Document Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-11-25 | Architecture Team | Initial design specification |

---

**END OF DOCUMENT**
