# Werkflow Delegate Data Flow

**Last Updated**: 2026-04-01
**Related**: S19 — Business-Agnostic Delegate Layer

---

## Overview

Werkflow supports two data flow modes for process automation. In both modes, the BPMN engine orchestrates but owns no business domain data.

---

## Current State (Pre-S19)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              BPMN Process Instance                               │
│                                                                                  │
│  ┌────────────┐    ┌─────────────────────────────────┐    ┌────────────────┐   │
│  │ Start      │───>│ Service Task                     │───>│ User Task      │   │
│  │ Event      │    │ flowable:delegateExpression=      │    │ (approval)     │   │
│  │            │    │ #{createPurchaseOrderDelegate}    │    │                │   │
│  └────────────┘    └────────────────┬────────────────┘    └────────────────┘   │
└───────────────────────────────────────┼─────────────────────────────────────────┘
                                        │ DelegateExecution
                                        │ (reads/writes process variables)
                       ┌────────────────▼────────────────┐
                       │     Domain-Specific Delegate      │
                       │  CreatePurchaseOrderDelegate      │
                       │                                   │
                       │  @Value("${app.services           │
                       │    .procurement.url}")             │
                       │  hardcoded field names:           │
                       │    requestId, selectedQuotationId │
                       │    poNumber                       │
                       └────────────────┬────────────────┘
                                        │ RestTemplate HTTP POST
                                        │ (single global URL, all tenants)
                       ┌────────────────▼────────────────┐
                       │     Internal Business Service     │
                       │  services/procurement             │
                       │  POST /api/workflow/              │
                       │    procurement/create-po          │
                       └─────────────────────────────────┘

PROBLEM: 16 domain delegates × hardcoded URLs × hardcoded field names
         = cannot support multi-tenancy or external systems
```

---

## Target State (Post-S19) — Two Data Flow Modes

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              BPMN Process Instance                               │
│                                                                                  │
│  ┌────────────┐    ┌─────────────────────────────────────────────────────────┐ │
│  │ Start      │───>│ Service Task (BPMN extension properties)                 │ │
│  │ Event      │    │                                                           │ │
│  │            │    │  flowable:delegateExpression=#{externalApiCallDelegate}  │ │
│  └────────────┘    │  flowable:field name="endpointKey"  value="procurement" │ │
│                    │  flowable:field name="path"   value="/api/create-po"    │ │
│                    │  flowable:field name="method"  value="POST"             │ │
│                    │  flowable:field name="requestBody"                       │ │
│                    │    value="requestId:${requestId},                        │ │
│                    │           quotationId:${selectedQuotationId}"            │ │
│                    │  flowable:field name="extractFields"                     │ │
│                    │    value="poNumber:$.poNumber"                           │ │
│                    │  flowable:field name="secretRef" value="proc-api-key"   │ │
│                    └────────────────────────┬────────────────────────────────┘ │
└────────────────────────────────────────────┼──────────────────────────────────┘
                                              │ DelegateExecution
                                              │
                         ┌────────────────────▼──────────────────────────────┐
                         │           ExternalApiCallDelegate                   │
                         │                                                     │
                         │  1. Resolve tenantId from execution context        │
                         │  2. TenantEndpointResolver.resolve(                │
                         │       tenantId, endpointKey)                       │
                         │     → checks TenantServiceEndpoint table           │
                         │     → falls back to global app.services.* config  │
                         │  3. SecretsResolver.resolve(secretRef)             │
                         │  4. SsrfGuard.validate(resolvedUrl)               │
                         │  5. Build POST body from requestBody spec          │
                         │  6. HTTP call via RestClient                       │
                         │  7. JSONPath extraction → process variables        │
                         │  8. ProcessAuditLog write                          │
                         └────────────────────┬──────────────────────────────┘
                                              │
                              ┌───────────────┴──────────────┐
                              │                               │
                    ┌─────────▼──────────┐       ┌───────────▼──────────┐
                    │   Mode A           │       │   Mode B              │
                    │   Built-in Service │       │   External System     │
                    │                   │       │                       │
                    │ TenantServiceEndpoint     │ TenantServiceEndpoint │
                    │ endpointKey=      │       │ endpointKey=          │
                    │ "procurement"     │       │ "erp-connector"       │
                    │ → http://         │       │ → https://            │
                    │   procurement:8085│       │   erp.client.com      │
                    │                   │       │                       │
                    │ Werkflow-managed  │       │ Customer-managed      │
                    │ internal service  │       │ external REST API     │
                    └───────────────────┘       └───────────────────────┘
```

---

## Tenant Endpoint Resolution Chain

```
ExternalApiCallDelegate.execute()
        │
        │ endpointKey = "procurement"
        │ tenantId    = from execution variable or JWT claim
        │
        ▼
TenantEndpointResolver.resolve(tenantId, endpointKey)
        │
        ├─── Step 1: Query TenantServiceEndpoint table
        │    WHERE tenant_id = ? AND endpoint_key = ?
        │    Cache: Caffeine, TTL 60s, key=(tenantId, endpointKey)
        │
        │    ┌─────────────────────────────────────────────────┐
        │    │ tenant_id │ endpoint_key  │ base_url            │
        │    │───────────│───────────────│─────────────────────│
        │    │ tenant-A  │ procurement   │ http://proc:8085    │ ← built-in
        │    │ tenant-A  │ erp-connector │ https://erp.corp.io │ ← external
        │    │ tenant-B  │ procurement   │ https://custom.io   │ ← overridden
        │    └─────────────────────────────────────────────────┘
        │
        ├─── Step 2 (fallback): global app.services.<endpointKey>.url
        │
        └─── Step 3 (fallback): throw ConfigurationException
```

---

## Request Body Construction

```
BPMN requestBody spec (field-mapping CSV):
  "requestId:${requestId}, quotationId:${selectedQuotationId}, tenantCode:${tenantCode}"
         │                         │                                  │
         │ variable name           │ process variable reference       │ literal or variable
         ▼                         ▼                                  ▼
  {
    "requestId":       "REQ-0042",          ← resolved from execution.getVariable("requestId")
    "quotationId":     "QT-0017",           ← resolved from execution.getVariable("selectedQuotationId")
    "tenantCode":      "acme"               ← resolved from execution.getVariable("tenantCode")
  }

Symmetric with extractFields (response):
  extractFields: "poNumber:$.poNumber, vendorId:$.vendor.id"
         │                │
         │ process var    │ JSONPath into response body
         ▼                ▼
  execution.setVariable("poNumber",  response.poNumber)
  execution.setVariable("vendorId",  response.vendor.id)
```

---

## Comparison: Industry Approaches

```
┌──────────────────┬────────────────────────────┬─────────────────────────────────┐
│ Platform         │ Adapter Abstraction         │ Config Surface                  │
├──────────────────┼────────────────────────────┼─────────────────────────────────┤
│ Camunda 8        │ Connector (outbound)        │ Element Templates (JSON schema) │
│                  │ ConnectorFunction interface │ FEEL expressions in Modeler     │
│                  │ ConnectorContext (input map) │ Secrets injected at runtime    │
├──────────────────┼────────────────────────────┼─────────────────────────────────┤
│ Temporal         │ Activity (typed interface)  │ ActivityOptions (retry, timeout)│
│                  │ ActivityEnvironment         │ Workflow code (not BPMN XML)    │
│                  │ No generic HTTP built-in    │ Custom HTTP activity per team   │
├──────────────────┼────────────────────────────┼─────────────────────────────────┤
│ MuleSoft         │ HTTP Request Connector      │ Connector config (base URL,auth)│
│                  │ Operation (method, path,    │ Connection config per env       │
│                  │   body, headers, query)     │ Property placeholders           │
├──────────────────┼────────────────────────────┼─────────────────────────────────┤
│ Werkflow (S19)   │ ExternalApiCallDelegate     │ BPMN flowable:field properties  │
│                  │ Single JavaDelegate class   │ endpointKey + path + method     │
│                  │ Flowable Expression fields  │ requestBody + extractFields CSV │
│                  │ TenantEndpointResolver      │ secretRef → SecretsResolver     │
└──────────────────┴────────────────────────────┴─────────────────────────────────┘

Shared pattern across all four:
  1. One adapter class / interface — not one per domain
  2. Endpoint identity (base URL) is runtime config, not compile-time
  3. Auth/secrets are injected by the runtime, not embedded in the adapter
  4. Request shape and response extraction are BPMN/workflow config, not Java code
  5. Engine has zero knowledge of what the downstream system does
```

---

## What Gets Deleted in S19

```
services/engine/src/main/java/com/werkflow/engine/
├── delegate/
│   ├── procurement/                     ← DELETE all 8 classes
│   │   ├── CalculateTotalCostDelegate.java
│   │   ├── CreatePurchaseOrderDelegate.java
│   │   ├── CreatePurchaseRequestDelegate.java
│   │   ├── FetchVendorsDelegate.java
│   │   ├── RequestQuotationsDelegate.java
│   │   ├── SendPOToVendorDelegate.java
│   │   ├── UpdateProcurementApprovedDelegate.java
│   │   └── UpdateProcurementRejectedDelegate.java
│   ├── transfer/                        ← DELETE all 8 classes
│   │   ├── CheckCustodyDelegate.java
│   │   ├── CreateTransferRecordDelegate.java
│   │   ├── CreateTransferRequestDelegate.java
│   │   ├── UpdateAssetLocationDelegate.java
│   │   ├── UpdateCustodyDelegate.java
│   │   ├── UpdateTransferCompletedDelegate.java
│   │   ├── UpdateTransferRejectedDelegate.java
│   │   └── VerifyAssetDelegate.java
│   ├── InitiateProcurementDelegate.java ← DELETE (HTTP wrapper)
│   ├── CreateAssetRequestDelegate.java  ← DELETE (HTTP wrapper)
│   ├── FulfillAssetRequestDelegate.java ← DELETE (HTTP wrapper)
│   ├── RejectAssetRequestDelegate.java  ← DELETE (HTTP wrapper)
│   ├── CheckStockDelegate.java          ← DELETE (HTTP wrapper)
│   ├── TaskAssignmentDelegate.java      ← KEEP (engine-level)
│   └── ApprovalTaskCompletionListener.java ← KEEP (engine-level)
└── action/
    └── ExternalApiCallDelegate.java     ← EXTEND with requestBody + TenantEndpointResolver
```
