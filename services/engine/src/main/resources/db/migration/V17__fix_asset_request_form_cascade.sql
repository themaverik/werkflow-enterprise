-- ================================================================================================
-- Fix Asset Request Form: category filter and asset definition cascade
-- ================================================================================================
-- Problems fixed:
--   1. categoryId field was fetching all categories (root + subcategories) — now uses leafOnly=true
--      so only subcategories (e.g. Laptop, Monitor) appear, which are the ones with asset definitions.
--   2. assetDefinitionId field used valuesExpression FEEL filter which had a naming collision
--      (item.categoryId = categoryId is ambiguous — always true). Now uses valuesKey directly since
--      the backend endpoint filters by ?categoryId= correctly.
--
-- Optional business workflow context: Patch for the bundled asset-request example workflow.
-- Safe to run without the optional business module — updates form_schemas only.
-- ================================================================================================

UPDATE form_schemas
SET schema_json = '{
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
                    "filter": { "leafOnly": true },
                    "valuesKey": "categoryOptions"
                }
            }
        },
        {
            "type": "select",
            "key": "assetDefinitionId",
            "label": "Asset",
            "valuesKey": "assetDefinitions",
            "validate": { "required": true },
            "properties": {
                "dataSource": {
                    "url": "/api/business/asset-definitions",
                    "labelField": "name",
                    "valueField": "id",
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
updated_by = 'system'
WHERE form_key = 'asset-request-form';
