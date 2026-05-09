-- V21: Demo seed — legacy HRIS read-only database connector
-- Inserts a system connector definition for the demo H2 HRIS datasource.
-- system=true prevents deletion via the UI (enforced at the service layer).

INSERT INTO connector_definition_v2 (key, version, tenant_id, definition_json, created_at, updated_at)
SELECT
    'legacy-hris-readonly',
    '1.0.0',
    'system',
    '{
      "metadata": {
        "key": "legacy-hris-readonly",
        "version": "1.0.0",
        "displayName": "Legacy HRIS (Read-Only)",
        "description": "Demo read-only connector for the in-memory H2 HRIS database. Exposes employee cost-centre lookup used by approval-authority workflows.",
        "category": "hr",
        "tags": ["demo", "hris", "read-only", "system"],
        "system": true
      },
      "spec": {
        "transport": {
          "type": "database",
          "config": {
            "datasourceRef": "demo-h2-hris",
            "dialect": "h2",
            "readOnly": true,
            "pool": { "minSize": 1, "maxSize": 3, "connectionTimeoutSeconds": 5, "idleTimeoutSeconds": 300 },
            "queries": [
              {
                "id": "getEmployeeCostCenter",
                "sql": "SELECT employee_id, full_name, cost_center, department_code FROM v_employee_cost_center WHERE employee_id = :employeeId",
                "parameters": [
                  { "name": "employeeId", "type": "string", "required": true }
                ],
                "resultMode": "object",
                "rowLimit": 1,
                "queryTimeoutSeconds": 5
              }
            ]
          }
        },
        "operations": [
          {
            "id": "getEmployeeCostCenter",
            "displayName": "Get Employee Cost Centre",
            "description": "Returns the cost centre and department for a single employee by ID.",
            "transportSpecific": { "queryRef": "getEmployeeCostCenter" },
            "inputSchema": {
              "type": "object",
              "required": ["employeeId"],
              "properties": { "employeeId": { "type": "string" } }
            },
            "outputSchema": {
              "type": "object",
              "properties": {
                "employee_id":      { "type": "string" },
                "full_name":        { "type": "string" },
                "cost_center":      { "type": "string" },
                "department_code":  { "type": "string" }
              }
            }
          }
        ]
      }
    }',
    now(),
    now()
WHERE NOT EXISTS (
    SELECT 1 FROM connector_definition_v2 WHERE key = 'legacy-hris-readonly' AND tenant_id = 'system'
);
