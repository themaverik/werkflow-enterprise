-- ================================================================================================
-- Seed Asset Request Form Schema
-- ================================================================================================
-- Description: Seeds the asset-request-form schema required by asset-request-process.bpmn20.xml
-- The BPMN start event references flowable:formKey="asset-request-form"; without this row
-- the engine returns 404 when loading the start form.
--
-- Optional business workflow context: This migration supports the bundled asset-request example
-- workflow. It is safe to run without the optional business module deployed — the form schema
-- row is stored in the engine database only and does not require business-service to be running.
-- ================================================================================================

INSERT INTO form_schemas (
    form_key,
    name,
    version,
    schema_json,
    description,
    form_type,
    is_active,
    created_by,
    updated_by
)
SELECT
    'asset-request-form',
    'Asset Request Form',
    1,
    '{
        "type": "default",
        "components": [
            {
                "type": "text",
                "text": "<h3>Asset Request</h3><p>Select an asset category and item, then complete the request details below.</p>"
            },
            {
                "type": "select",
                "key": "categoryId",
                "label": "Asset Category",
                "valuesKey": "categoryOptions",
                "validate": { "required": true },
                "properties": {
                    "dataSource": {
                        "url": "/api/business/asset-categories",
                        "labelField": "name",
                        "valueField": "id",
                        "filter": { "active": true }
                    }
                }
            },
            {
                "type": "select",
                "key": "assetDefinitionId",
                "label": "Asset",
                "valuesExpression": "= assetDefinitions[item.categoryId = categoryId]",
                "validate": { "required": true },
                "properties": {
                    "dataSource": {
                        "url": "/api/business/asset-definitions",
                        "labelField": "name",
                        "valueField": "id",
                        "extraFields": ["categoryId"],
                        "filter": { "active": true },
                        "dependsOn": "categoryId",
                        "dependsOnParam": "categoryId",
                        "valuesKey": "assetDefinitions"
                    }
                }
            },
            {
                "type": "number",
                "key": "quantity",
                "label": "Quantity",
                "defaultValue": 1,
                "validate": { "required": true, "min": 1 }
            },
            {
                "type": "select",
                "key": "officeLocation",
                "label": "Office Location",
                "validate": { "required": true },
                "values": [
                    { "label": "Kuala Lumpur", "value": "KL" },
                    { "label": "Penang", "value": "PG" },
                    { "label": "Johor Bahru", "value": "JB" },
                    { "label": "Remote", "value": "REMOTE" }
                ]
            },
            {
                "type": "datetime",
                "key": "deliveryDate",
                "label": "Required By Date (Optional)"
            },
            {
                "type": "textarea",
                "key": "justification",
                "label": "Justification",
                "validate": { "required": true, "minLength": 10 }
            },
            {
                "type": "textfield",
                "key": "requesterName",
                "label": "Your Name",
                "readonly": true,
                "validate": { "required": false }
            },
            {
                "type": "textfield",
                "key": "requesterEmail",
                "label": "Your Email",
                "readonly": true,
                "validate": { "required": false }
            }
        ]
    }',
    'Asset Request Form - dynamic category and asset selects via dataSource pattern',
    'TASK_FORM',
    true,
    'system',
    'system'
WHERE NOT EXISTS (
    SELECT 1 FROM form_schemas WHERE form_key = 'asset-request-form'
);
