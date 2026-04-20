-- Seed form schema for the Onboarding Checklist sample workflow.

INSERT INTO form_schemas (form_key, version, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'onboarding-checklist-form',
    1,
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {
                "type": "textfield",
                "id": "employeeName",
                "key": "employeeName",
                "label": "Employee Full Name",
                "validate": {
                    "required": true
                }
            },
            {
                "type": "textfield",
                "id": "employeeEmail",
                "key": "employeeEmail",
                "label": "Employee Email",
                "validate": {
                    "required": true,
                    "pattern": "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
                }
            },
            {
                "type": "select",
                "id": "department",
                "key": "department",
                "label": "Department",
                "validate": {
                    "required": true
                },
                "values": [
                    {"label": "Engineering", "value": "ENGINEERING"},
                    {"label": "Finance", "value": "FINANCE"},
                    {"label": "HR", "value": "HR"},
                    {"label": "Operations", "value": "OPERATIONS"},
                    {"label": "IT", "value": "IT"}
                ]
            },
            {
                "type": "datetime",
                "id": "startDate",
                "key": "startDate",
                "label": "Start Date",
                "subtype": "date",
                "validate": {
                    "required": true
                }
            },
            {
                "type": "textarea",
                "id": "equipmentNeeded",
                "key": "equipmentNeeded",
                "label": "Equipment Needed",
                "validate": {
                    "required": false
                },
                "description": "List equipment needed (e.g. Laptop, Monitor, Phone)"
            },
            {
                "type": "checkbox",
                "id": "buddyRequired",
                "key": "buddyRequired",
                "label": "Assign Manager Buddy",
                "defaultValue": true
            }
        ]
    }'::jsonb,
    'Onboarding Checklist Form — process start form for the employee onboarding sample workflow',
    'PROCESS_START',
    true,
    'system',
    'system'
) ON CONFLICT (form_key, version) DO NOTHING;
