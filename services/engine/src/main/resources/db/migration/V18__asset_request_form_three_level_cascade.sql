-- ================================================================================================
-- Asset Request Form: redesign to three-level cascade
-- ================================================================================================
-- Previous design showed subcategories (Laptop, Workstation) as the first selector, which
-- made no sense to users. Correct UX:
--   1. Asset Type  → root categories (IT, Office Assets, Vehicles)
--   2. Asset Category → subcategories filtered by selected type (Laptop, Monitor, ...)
--   3. Asset → definitions filtered by selected subcategory (MacBook Pro, Dell XPS, ...)
--
-- assetTypeId is a UI-only field (not a BPMN process variable).
-- categoryId (leaf subcategory) and assetDefinitionId are the process variables used by the BPMN.
--
-- Optional business workflow context: Redesign for the bundled asset-request example workflow.
-- Safe to run without the optional business module — updates form_schemas only.
-- ================================================================================================

UPDATE form_schemas
SET schema_json = '{
    "type": "default",
    "components": [
        {
            "type": "text",
            "text": "<h3>Asset Request</h3><p>Select an asset type, category, and item, then complete the request details below.</p>"
        },
        {
            "type": "select",
            "key": "assetTypeId",
            "label": "Asset Type",
            "valuesKey": "assetTypeOptions",
            "validate": { "required": true },
            "properties": {
                "dataSource": {
                    "url": "/api/business/asset-categories/root",
                    "labelField": "name",
                    "valueField": "id",
                    "valuesKey": "assetTypeOptions"
                }
            }
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
                    "valuesKey": "categoryOptions",
                    "dependsOn": "assetTypeId",
                    "dependsOnParam": "parentCategoryId"
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
                    "valuesKey": "assetDefinitions",
                    "dependsOn": "categoryId",
                    "dependsOnParam": "categoryId"
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
