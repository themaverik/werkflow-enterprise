package com.werkflow.engine.service;

import com.werkflow.engine.dto.FormDefinitionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for managing form definitions in Flowable
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormService {

    private final RepositoryService repositoryService;

    /**
     * Get a form definition by its key.
     * Returns a form schema compatible with Form.io that can be used by the frontend form engine.
     *
     * @param formKey The form key to retrieve
     * @return Form definition response containing the form schema
     */
    public FormDefinitionResponse getFormByKey(String formKey) {
        log.debug("Fetching form definition for form key: {}", formKey);

        // Build a basic form definition based on form key
        // In a production system, this would load actual form definitions from a database
        // or form repository service
        Map<String, Object> definition = buildFormDefinition(formKey);

        FormDefinitionResponse response = new FormDefinitionResponse();
        response.setKey(formKey);
        response.setDefinition(definition);
        response.setType("form");

        log.debug("Form definition retrieved for form key: {}", formKey);
        return response;
    }

    /**
     * Build a form definition schema based on form key.
     * This creates a Form.io compatible form that can be customized per form key.
     *
     * @param formKey The form key
     * @return Map containing the form definition with components as array
     */
    private Map<String, Object> buildFormDefinition(String formKey) {
        Map<String, Object> definition = new HashMap<>();

        // Basic form structure compatible with Form.io
        definition.put("display", "form");
        definition.put("title", formatFormTitle(formKey));
        definition.put("name", formKey);
        definition.put("path", formKey.toLowerCase().replace("-", ""));

        // Add form-specific components based on key as an array (required by Form.io)
        List<Map<String, Object>> components = buildComponentsForFormKey(formKey);
        definition.put("components", components);

        definition.put("type", "form");

        return definition;
    }

    /**
     * Build form components array based on the form key.
     * Returns components as a List which is required by Form.io schema.
     * Each component includes all required Form.io fields.
     *
     * @param formKey The form key
     * @return List of form components in Form.io format
     */
    private List<Map<String, Object>> buildComponentsForFormKey(String formKey) {
        List<Map<String, Object>> components = new ArrayList<>();

        // Form-specific component configurations
        switch (formKey) {
            case "procurement-request":
                components.add(createSelectComponent("Item Category", "itemCategory",
                    new String[]{"Electronics", "Office Supplies", "Equipment", "Services", "Raw Materials"}));
                components.add(createTextComponent("Item Description", "itemDescription"));
                components.add(createNumberComponent("Quantity", "quantity"));
                components.add(createNumberComponent("Estimated Budget", "estimatedBudget"));
                components.add(createTextAreaComponent("Business Justification", "businessJustification"));
                break;

            case "vendor-selection":
                components.add(createSelectComponent("Select Vendors", "selectedVendorIds",
                    new String[]{"Vendor 1", "Vendor 2", "Vendor 3"}));
                break;

            case "quotation-review":
                components.add(createSelectComponent("Select Quotation", "selectedQuotationId",
                    new String[]{"Quotation A", "Quotation B", "Quotation C"}));
                components.add(createTextAreaComponent("Review Notes", "reviewNotes"));
                break;

            case "procurement-approval":
                components.add(createSelectComponent("Decision", "approvalDecision",
                    new String[]{"APPROVED", "REJECTED"}));
                components.add(createTextAreaComponent("Approval Notes", "approvalNotes"));
                components.add(createTextAreaComponent("Rejection Reason (if rejected)", "rejectionReason"));
                break;

            default:
                // Generic form with basic text field
                components.add(createTextComponent("Enter value", "value"));
                break;
        }

        return components;
    }

    /**
     * Create a text input component with all required Form.io fields
     */
    private Map<String, Object> createTextComponent(String label, String key) {
        Map<String, Object> component = new HashMap<>();
        component.put("type", "textfield");
        component.put("label", label);
        component.put("key", key);
        component.put("placeholder", label);
        component.put("input", true);
        component.put("tableView", false);
        component.put("validate", new HashMap<String, Object>() {{
            put("required", false);
        }});
        component.put("persistent", true);
        return component;
    }

    /**
     * Create a text area component with all required Form.io fields
     */
    private Map<String, Object> createTextAreaComponent(String label, String key) {
        Map<String, Object> component = new HashMap<>();
        component.put("type", "textarea");
        component.put("label", label);
        component.put("key", key);
        component.put("placeholder", label);
        component.put("input", true);
        component.put("tableView", false);
        component.put("validate", new HashMap<String, Object>() {{
            put("required", false);
        }});
        component.put("persistent", true);
        return component;
    }

    /**
     * Create a number input component with all required Form.io fields
     */
    private Map<String, Object> createNumberComponent(String label, String key) {
        Map<String, Object> component = new HashMap<>();
        component.put("type", "number");
        component.put("label", label);
        component.put("key", key);
        component.put("placeholder", label);
        component.put("input", true);
        component.put("tableView", false);
        component.put("validate", new HashMap<String, Object>() {{
            put("required", false);
        }});
        component.put("persistent", true);
        return component;
    }

    /**
     * Create a select dropdown component with proper Form.io data structure
     */
    private Map<String, Object> createSelectComponent(String label, String key, String[] options) {
        Map<String, Object> component = new HashMap<>();
        component.put("type", "select");
        component.put("label", label);
        component.put("key", key);
        component.put("placeholder", "Select an option");
        component.put("input", true);
        component.put("tableView", false);
        component.put("validate", new HashMap<String, Object>() {{
            put("required", false);
        }});
        component.put("persistent", true);

        // Build data options as array (proper Form.io format)
        List<Map<String, String>> dataValues = new ArrayList<>();
        for (String option : options) {
            Map<String, String> optionMap = new HashMap<>();
            optionMap.put("label", option);
            optionMap.put("value", option.toLowerCase().replace(" ", "-"));
            dataValues.add(optionMap);
        }

        // Set data with values array (Form.io standard format)
        Map<String, Object> data = new HashMap<>();
        data.put("values", dataValues);
        component.put("data", data);

        return component;
    }

    /**
     * Format form key to a readable title
     */
    private String formatFormTitle(String formKey) {
        String[] parts = formKey.replace("-", " ").split(" ");
        StringBuilder title = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                title.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1))
                    .append(" ");
            }
        }
        return title.toString().trim();
    }
}
