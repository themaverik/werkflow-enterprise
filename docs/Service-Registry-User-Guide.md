# Service Registry User Guide

**Version**: 1.0
**Last Updated**: 2025-11-24
**Phase**: 4 - Frontend Integration Complete

## Overview

The Service Registry is a centralized system for managing microservice configurations across the Werkflow platform. It enables no-code workflow creation by providing dynamic service URL resolution and endpoint discovery.

## Table of Contents

1. [Getting Started](#getting-started)
2. [Registering Services](#registering-services)
3. [Configuring Environment URLs](#configuring-environment-urls)
4. [Using Services in Workflows](#using-services-in-workflows)
5. [Health Monitoring](#health-monitoring)
6. [Troubleshooting](#troubleshooting)

---

## Getting Started

### Prerequisites

1. Backend Service Registry API must be running at `http://localhost:8081/api/services`
2. Admin Portal frontend must be accessible at `http://localhost:3000`
3. User must have admin privileges to manage service registry

### Accessing the Service Registry

1. Navigate to the Admin Portal: `http://localhost:3000`
2. Click on "Services" in the left sidebar
3. You will see the Service Registry dashboard

---

## Registering Services

### Step 1: Navigate to Service Registry

From the Admin Portal, click on **Services** in the navigation menu.

### Step 2: View Registered Services

The dashboard displays:
- Total number of registered services
- Count of healthy services
- Average response time across all services

### Step 3: Register a New Service

**Note**: For Phase 4, services are registered via backend seed data. Frontend registration UI will be available in Phase 4.2.

To register services via API:

```bash
curl -X POST http://localhost:8081/api/services \
  -H "Content-Type: application/json" \
  -d '{
    "serviceName": "finance",
    "displayName": "Finance Service",
    "description": "Budget management and financial operations",
    "serviceType": "REST_API",
    "baseUrl": "http://finance-service:8084/api"
  }'
```

### Default Registered Services

The following services are pre-registered:

1. **HR Service** - `http://hr-service:8082/api`
   - Leave management
   - Employee operations
   - Training requests

2. **Finance Service** - `http://finance-service:8084/api`
   - Budget checking
   - Budget allocation
   - CapEx approvals

3. **Procurement Service** - `http://procurement-service:8085/api`
   - Purchase requisitions
   - Purchase orders
   - Vendor management

4. **Inventory Service** - `http://inventory-service:8086/api`
   - Stock checking
   - Stock reservation
   - Asset transfers

5. **Admin Service** - `http://admin-service:8083/api`
   - User management
   - Department management
   - System configuration

---

## Configuring Environment URLs

Services can have different URLs for different environments (development, staging, production).

### Understanding Environment URLs

Each service can have multiple environment-specific URLs:
- **Development**: Used during local development (e.g., `http://localhost:8084`)
- **Staging**: Used in staging environment (e.g., `https://staging-api.company.com/finance`)
- **Production**: Used in production (e.g., `https://api.company.com/finance`)

### Adding Environment URLs

To configure environment-specific URLs via API:

```bash
curl -X POST http://localhost:8081/api/services/finance-service/urls \
  -H "Content-Type: application/json" \
  -d '{
    "environment": "development",
    "baseUrl": "http://localhost:8084/api",
    "priority": 1,
    "isActive": true
  }'

curl -X POST http://localhost:8081/api/services/finance-service/urls \
  -H "Content-Type: application/json" \
  -d '{
    "environment": "staging",
    "baseUrl": "https://staging-finance.company.com/api",
    "priority": 1,
    "isActive": true
  }'

curl -X POST http://localhost:8081/api/services/finance-service/urls \
  -H "Content-Type: application/json" \
  -d '{
    "environment": "production",
    "baseUrl": "https://finance.company.com/api",
    "priority": 1,
    "isActive": true
  }'
```

### Priority and Active Status

- **Priority**: If multiple URLs exist for the same environment, the one with highest priority is used
- **isActive**: Only active URLs are resolved. Set to `false` to temporarily disable a URL without deleting it

---

## Using Services in Workflows

### Workflow Integration Overview

When a workflow starts, the `ProcessVariableInjector` automatically injects service URLs as process variables. These variables can then be accessed by service tasks.

### Process Variable Format

For each registered service, the following variable is injected:

```
{serviceName}_service_url = {resolvedUrl}
```

Example:
```
finance_service_url = http://finance-service:8084/api
procurement_service_url = http://procurement-service:8085/api
inventory_service_url = http://inventory-service:8086/api
```

### Using Service URLs in BPMN

#### Method 1: RestServiceDelegate with Variable Expression

```xml
<serviceTask id="checkBudget" name="Check Budget">
  <extensionElements>
    <flowable:field name="url">
      <flowable:expression>${finance_service_url}/budget/check</flowable:expression>
    </flowable:field>
    <flowable:field name="method">
      <flowable:string>POST</flowable:string>
    </flowable:field>
    <flowable:field name="requestBody">
      <flowable:expression>
        {
          "departmentId": "${departmentId}",
          "amount": ${amount}
        }
      </flowable:expression>
    </flowable:field>
    <flowable:field name="responseVariable">
      <flowable:string>budgetCheckResponse</flowable:string>
    </flowable:field>
  </extensionElements>
</serviceTask>
```

#### Method 2: Using Service Selector in BPMN Designer (Phase 4.2+)

1. Open BPMN Designer
2. Select a Service Task
3. In the Properties Panel:
   - Select "REST Service Delegate" from delegate dropdown
   - Choose "Finance Service" from service selector
   - Select "/budget/check" endpoint
   - URL is automatically populated with `${finance_service_url}/budget/check`

### Dynamic URL Resolution

URLs are resolved at workflow **start time** based on the current environment:

```java
// ProcessVariableInjector automatically runs on workflow start
String environment = applicationEnvironment; // e.g., "development"
String financeUrl = serviceRegistry.resolveUrl("finance", environment);
execution.setVariable("finance_service_url", financeUrl);
```

### Benefits of Dynamic URLs

1. **No Hard-Coding**: URLs are not hardcoded in BPMN files
2. **Environment-Aware**: Same BPMN works in dev, staging, and production
3. **No Restart Required**: URL changes take effect in new workflow instances immediately
4. **Centralized Management**: All service URLs managed in one place

---

## Health Monitoring

### Viewing Service Health

The Service Registry dashboard shows health status for each service:

- **Green indicator**: Service is healthy (responding in <500ms)
- **Red indicator**: Service is unhealthy (not responding or error)
- **Gray indicator**: Health status unknown (not checked yet)

### Automatic Health Checks

The backend automatically checks service health every 30 seconds:

1. Sends HTTP GET request to service health endpoint
2. Records response time
3. Updates health status (HEALTHY / UNHEALTHY / UNKNOWN)
4. Stores last 10 health check results

### Manual Health Check

To trigger a manual health check:

```bash
curl -X POST http://localhost:8081/api/services/finance-service/health/check
```

Response:
```json
{
  "status": "HEALTHY",
  "responseTime": 45,
  "timestamp": "2025-11-24T10:30:00Z"
}
```

### Health Check Endpoints

Services must implement health check endpoints:

```
GET /actuator/health (Spring Boot Actuator)
GET /health (Custom endpoint)
```

Response format:
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP"
    }
  }
}
```

---

## Troubleshooting

### Issue: Service Registry Dashboard Shows Error

**Symptom**: "Backend Service Not Available" error message

**Cause**: Backend Service Registry API is not running

**Solution**:
1. Start the backend services:
   ```bash
   cd infrastructure/docker
   docker-compose up -d
   ```
2. Verify backend is running:
   ```bash
   curl http://localhost:8081/actuator/health
   ```
3. Click "Retry Connection" button in the dashboard

### Issue: Service Not Appearing in Dropdown

**Symptom**: Service is registered but doesn't show in BPMN Designer dropdown

**Cause**: Frontend cache not refreshed

**Solution**:
1. Click "Refresh" button in Service Registry dashboard
2. Wait 30 seconds for cache to refresh
3. Reload BPMN Designer page
4. Check browser console for errors

### Issue: Workflow Using Wrong Service URL

**Symptom**: Workflow calls `http://localhost:8084` instead of production URL

**Cause**: Environment not configured correctly

**Solution**:
1. Check `application.yml` for correct environment:
   ```yaml
   app:
     environment: ${APP_ENVIRONMENT:development}
   ```
2. Verify environment URLs registered:
   ```bash
   curl http://localhost:8081/api/services/finance-service/urls
   ```
3. Ensure correct environment is set in deployment:
   - Docker Compose: Set `APP_ENVIRONMENT=development`
   - Kubernetes: Set environment variable in deployment YAML

### Issue: Service Health Check Always Shows UNKNOWN

**Symptom**: Health status never updates from UNKNOWN

**Cause**: Health check endpoint not accessible or returning wrong format

**Solution**:
1. Verify service has health endpoint:
   ```bash
   curl http://finance-service:8084/actuator/health
   ```
2. Check service logs for health check errors
3. Ensure health endpoint returns JSON with `status` field
4. Trigger manual health check to see error:
   ```bash
   curl -X POST http://localhost:8081/api/services/finance-service/health/check
   ```

### Issue: New Service URL Not Being Used

**Symptom**: Changed service URL but workflow still uses old URL

**Cause**: Process variables are injected at workflow start time

**Solution**:
1. URL changes only affect NEW workflow instances
2. Running workflows continue using old URL (by design)
3. To force update:
   - Wait for running workflows to complete
   - Start new workflow instance
   - New instance will use updated URL

### Issue: API Returns 404 for Service

**Symptom**: `Service 'xyz' not found` error

**Cause**: Service name mismatch or not registered

**Solution**:
1. Check exact service name in registry:
   ```bash
   curl http://localhost:8081/api/services | jq '.[].serviceName'
   ```
2. Ensure service name matches exactly (case-sensitive)
3. Register service if missing

---

## API Reference

### Get All Services

```http
GET /api/services
```

Response:
```json
[
  {
    "id": "1",
    "serviceName": "finance",
    "displayName": "Finance Service",
    "description": "Budget management",
    "serviceType": "REST_API",
    "healthStatus": "HEALTHY",
    "baseUrl": "http://finance-service:8084/api",
    "responseTime": 45,
    "lastChecked": "2025-11-24T10:30:00Z"
  }
]
```

### Get Service by ID

```http
GET /api/services/{id}
```

### Get Service by Name

```http
GET /api/services/by-name/{serviceName}
```

### Create Service

```http
POST /api/services
Content-Type: application/json

{
  "serviceName": "finance",
  "displayName": "Finance Service",
  "description": "Budget management",
  "serviceType": "REST_API",
  "baseUrl": "http://finance-service:8084/api"
}
```

### Update Service

```http
PUT /api/services/{id}
Content-Type: application/json

{
  "displayName": "Finance Service (Updated)",
  "description": "Budget and financial operations"
}
```

### Delete Service

```http
DELETE /api/services/{id}
```

### Get Service Environment URLs

```http
GET /api/services/{id}/urls
```

### Add/Update Environment URL

```http
POST /api/services/{id}/urls
Content-Type: application/json

{
  "environment": "production",
  "baseUrl": "https://api.company.com/finance",
  "priority": 1,
  "isActive": true
}
```

### Resolve Service URL

```http
GET /api/services/resolve/{serviceName}?env={environment}
```

Response:
```json
{
  "url": "http://finance-service:8084/api"
}
```

### Get Service Health

```http
GET /api/services/{id}/health
```

Response:
```json
{
  "status": "HEALTHY",
  "responseTime": 45,
  "timestamp": "2025-11-24T10:30:00Z"
}
```

### Trigger Health Check

```http
POST /api/services/{id}/health/check
```

---

## Best Practices

### Service Naming

- Use lowercase with hyphens: `finance-service`, `hr-service`
- Keep names short and descriptive
- Don't include environment in name (use environment URLs instead)

### Environment Configuration

- Always configure all three environments (dev, staging, prod)
- Use priority=1 for primary URL
- Set isActive=false to temporarily disable URL without deleting
- Test URL changes in dev before applying to staging/prod

### Health Monitoring

- Implement health endpoints in all services
- Return consistent health response format
- Include database and dependency checks in health endpoint
- Monitor health dashboard regularly

### Workflow Design

- Always use `${serviceName_service_url}` variable syntax
- Don't hardcode URLs in BPMN files
- Test workflows in dev environment before deploying
- Document which services each workflow depends on

---

## Support

For issues or questions:

1. Check this User Guide
2. Review [Troubleshooting](#troubleshooting) section
3. Check backend logs: `docker-compose logs engine`
4. Check frontend console for errors
5. Contact platform team for additional support

---

## Appendix A: Adding Sample Forms

### Sample Forms Available

As of Version 7 migration, Werkflow includes 6 sample forms:

1. **capex-request** - Capital expenditure request (PROCESS_START)
2. **capex-approval** - CapEx approval decision (APPROVAL)
3. **employee-onboarding** - New employee information (TASK_FORM)
4. **asset-transfer** - Asset transfer between departments (TASK_FORM)
5. **contact-request** - General inquiry form (CUSTOM)
6. **purchase-request** - Purchase requisition (TASK_FORM)

### Using Forms in BPMN

Reference forms in your BPMN processes using the `flowable:formKey` attribute:

```xml
<!-- Start Event with Form -->
<startEvent id="start" name="Submit Request" flowable:formKey="capex-request">
  <documentation>User submits a capital expenditure request</documentation>
</startEvent>

<!-- User Task with Form -->
<userTask id="review" name="Manager Review"
          flowable:candidateGroups="FINANCE_MANAGER"
          flowable:formKey="capex-approval">
  <documentation>Manager reviews and approves/rejects request</documentation>
</userTask>
```

### Adding New Forms

To add new forms to Werkflow:

#### Option 1: Via Admin Portal UI (Recommended)
1. Navigate to Admin Portal: `http://localhost:3000/forms`
2. Click "Create New Form"
3. Use the Form.js visual editor to design your form
4. Save with a unique form_key (e.g., "leave-request")
5. Form is immediately available for use in BPMN

#### Option 2: Via Flyway Migration
1. Create new migration file: `V{next}__add_{form_name}_form.sql`
2. Add INSERT statement (see V7 migration for examples)
3. Deploy application - Flyway runs migration automatically

Example migration:
```sql
INSERT INTO form_schemas (
    form_key,
    version,
    schema_json,
    description,
    form_type,
    is_active,
    created_by,
    updated_by
) VALUES (
    'your-form-key',
    1,
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {
                "type": "textfield",
                "id": "fieldName",
                "key": "fieldName",
                "label": "Field Label",
                "validate": {"required": true}
            }
        ]
    }'::jsonb,
    'Your form description',
    'TASK_FORM',
    true,
    'system',
    'system'
) ON CONFLICT (form_key, version) DO NOTHING;
```

### Form Types

- **PROCESS_START**: Used on start events to collect initial data
- **TASK_FORM**: Used on user tasks for data collection
- **APPROVAL**: Used for approval/rejection decisions
- **CUSTOM**: General-purpose forms not tied to workflows

### Form Field Types

Supported Form.js field types:
- `textfield`: Single-line text
- `textarea`: Multi-line text
- `number`: Numeric input
- `select`: Dropdown selection
- `checkbox`: Boolean checkbox
- `radio`: Radio button group
- `date`: Date picker
- `text`: Read-only display text

### Validation Rules

Common validation options:
```javascript
"validate": {
    "required": true,           // Field is required
    "minLength": 10,            // Minimum text length
    "maxLength": 100,           // Maximum text length
    "min": 0,                   // Minimum number value
    "max": 1000,                // Maximum number value
    "pattern": "^[0-9]+$"       // Regex pattern
}
```

---

**End of Service Registry User Guide**
