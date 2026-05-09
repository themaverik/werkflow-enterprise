-- Seed the werkflow-erp-events webhook connector definition for demo tenant.
-- This connector describes inbound events published by werkflow-erp (vendor
-- status changes and PO state transitions) and is used by the webhook receiver
-- to resolve HMAC config, replay settings, and message correlation mappings.

INSERT INTO connector_definition_v2 (id, key, version, tenant_id, definition_json, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    'werkflow-erp-events',
    '1.0.0',
    'default',
    '{
      "metadata": {
        "key": "werkflow-erp-events",
        "name": "Werkflow ERP Events",
        "version": "1.0.0",
        "description": "Inbound webhook events from the Werkflow ERP service (vendor status, PO transitions)",
        "category": "integration",
        "icon": "database"
      },
      "transport": {
        "type": "webhook",
        "webhook": {
          "hmac": {
            "strategy": "generic",
            "headerName": "X-Werkflow-Signature",
            "secretRef": "env:WERKFLOW_ERP_WEBHOOK_SECRET"
          },
          "replayWindowSeconds": 7200,
          "idempotencyKeyHeader": "X-Idempotency-Key",
          "events": [
            {
              "name": "VendorStatusChanged",
              "messageName": "VendorStatusChanged",
              "correlationVariable": "vendorId",
              "description": "Fired when a vendor is blacklisted or reactivated"
            },
            {
              "name": "PurchaseOrderStatusChanged",
              "messageName": "PurchaseOrderStatusChanged",
              "correlationVariable": "purchaseOrderId",
              "description": "Fired when a PO transitions to APPROVED or ORDERED"
            }
          ]
        }
      },
      "operations": [
        {
          "operationId": "vendorStatusChanged",
          "name": "Vendor Status Changed",
          "description": "Fired when a vendor status changes (e.g. BLACKLISTED)",
          "method": "webhook-receive",
          "input": {
            "schema": {
              "type": "object",
              "required": ["vendorId", "tenantId", "newStatus"],
              "properties": {
                "vendorId":   { "type": "integer" },
                "tenantId":   { "type": "string" },
                "oldStatus":  { "type": "string", "enum": ["ACTIVE", "INACTIVE", "BLACKLISTED"] },
                "newStatus":  { "type": "string", "enum": ["ACTIVE", "INACTIVE", "BLACKLISTED"] },
                "occurredAt": { "type": "string", "format": "date-time" }
              }
            }
          }
        },
        {
          "operationId": "purchaseOrderStatusChanged",
          "name": "Purchase Order Status Changed",
          "description": "Fired when a PO transitions to APPROVED or ORDERED",
          "method": "webhook-receive",
          "input": {
            "schema": {
              "type": "object",
              "required": ["purchaseOrderId", "tenantId", "newStatus"],
              "properties": {
                "purchaseOrderId": { "type": "integer" },
                "poNumber":        { "type": "string" },
                "tenantId":        { "type": "string" },
                "oldStatus":       { "type": "string" },
                "newStatus":       { "type": "string" },
                "occurredAt":      { "type": "string", "format": "date-time" }
              }
            }
          }
        }
      ]
    }',
    NOW(),
    NOW()
)
ON CONFLICT DO NOTHING;
