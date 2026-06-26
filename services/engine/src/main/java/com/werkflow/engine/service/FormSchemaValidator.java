package com.werkflow.engine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.werkflow.engine.exception.FormFieldTypeNotImplementedException;
import com.werkflow.engine.exception.FormValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for validating form-js schemas and form data submissions.
 *
 * Field type categories (ADR-007):
 *   A — VARIABLE_TYPES: produce process variables on submit
 *   B — DISPLAY_TYPES: display-only; excluded from variablesToSave
 *   C — SERVICE_TYPES: service-integration fields; not yet supported on submit (501)
 *   D — anything outside A+B+C: invalid (400)
 */
@Service
@Slf4j
public class FormSchemaValidator {

    // NOTE: form-js has no top-level "date" or "time" field type — both are
    // subtypes of "datetime" (subtype: date|time|datetime). Listing them here
    // let malformed forms pass validation yet silently fail to render. Date/time
    // fields must be authored as type "datetime" with the appropriate subtype.
    static final Set<String> VARIABLE_TYPES = Set.of(
            "textfield", "number", "textarea", "checkbox", "radio", "select",
            "datetime", "email", "checklist", "taglist", "expression",
            "group", "columns"
    );

    static final Set<String> DISPLAY_TYPES = Set.of(
            "html", "text", "button", "image", "spacer", "separator"
    );

    static final Set<String> SERVICE_TYPES = Set.of(
            "dynamiclist"
    );

    /**
     * Pathed/container types that do NOT require a top-level `key` —
     * form-js identifies their data shape via either nested children
     * (group, columns: inline grouping; children's own keys are the
     * data properties) or a `path` field (dynamiclist: array root).
     * Mirrors form-js's `config.keyed` vs `config.pathed` distinction.
     */
    static final Set<String> PATHED_TYPES = Set.of(
            "group", "columns", "dynamiclist"
    );

    private static final Set<String> VALID_FIELD_TYPES;
    static {
        Set<String> all = new HashSet<>();
        all.addAll(VARIABLE_TYPES);
        all.addAll(DISPLAY_TYPES);
        all.addAll(SERVICE_TYPES);
        VALID_FIELD_TYPES = Collections.unmodifiableSet(all);
    }

    /**
     * Validate a form-js schema structure
     * @param schema The JSON schema to validate
     * @throws FormValidationException if schema is invalid
     */
    public void validateFormSchema(JsonNode schema) {
        Map<String, List<String>> errors = new HashMap<>();

        if (schema == null || schema.isNull()) {
            errors.put("schema", List.of("Schema cannot be null"));
            throw new FormValidationException("Invalid form schema", errors);
        }

        // Validate schema has required properties
        if (!schema.has("type")) {
            errors.put("schema.type", List.of("Schema must have a 'type' property"));
        } else if (!"default".equals(schema.get("type").asText())) {
            errors.put("schema.type", List.of("Schema type must be 'default'"));
        }

        // Validate components array exists
        if (!schema.has("components")) {
            errors.put("schema.components", List.of("Schema must have a 'components' array"));
        } else if (!schema.get("components").isArray()) {
            errors.put("schema.components", List.of("Components must be an array"));
        } else {
            validateComponents(schema.get("components"), errors, "");
        }

        if (!errors.isEmpty()) {
            throw new FormValidationException("Form schema validation failed", errors);
        }

        log.info("Form schema validation successful");
    }

    /**
     * Validate form components recursively
     */
    private void validateComponents(JsonNode components, Map<String, List<String>> errors, String path) {
        if (!components.isArray()) {
            errors.put(path + "components", List.of("Components must be an array"));
            return;
        }

        for (int i = 0; i < components.size(); i++) {
            JsonNode component = components.get(i);
            String componentPath = path + "components[" + i + "]";

            // Validate component has type
            if (!component.has("type")) {
                addError(errors, componentPath + ".type", "Component must have a type");
                continue;
            }

            String type = component.get("type").asText();

            // Validate type is valid
            if (!VALID_FIELD_TYPES.contains(type)) {
                addError(errors, componentPath + ".type",
                        "Invalid component type: " + type + ". Must be one of: " + VALID_FIELD_TYPES);
            }

            // Validate component has key (unless it's a display-only component)
            if (requiresKey(type) && !component.has("key")) {
                addError(errors, componentPath + ".key", "Component of type '" + type + "' must have a key");
            }

            // Validate nested components for container types
            if (isContainerType(type) && component.has("components")) {
                validateComponents(component.get("components"), errors, componentPath + ".");
            }
        }
    }

    /**
     * Returns the set of form data keys whose schema type is display-only (Category B).
     * Used by TaskFormService to filter keys before storing process variables.
     */
    public Set<String> extractDisplayOnlyKeys(JsonNode schema) {
        Map<String, JsonNode> defs = extractFieldDefinitions(schema);
        Set<String> result = new HashSet<>();
        for (Map.Entry<String, JsonNode> e : defs.entrySet()) {
            String type = e.getValue().has("type") ? e.getValue().get("type").asText() : "";
            if (DISPLAY_TYPES.contains(type)) {
                result.add(e.getKey());
            }
        }
        return result;
    }

    /**
     * Check if component type requires a top-level `key`. Display types
     * (presentational, no submitted data) and pathed types (containers
     * grouping nested fields, or dynamiclist with a `path`) are exempt.
     */
    private boolean requiresKey(String type) {
        return !DISPLAY_TYPES.contains(type) && !PATHED_TYPES.contains(type);
    }

    /**
     * Check if component type can contain other components
     */
    private boolean isContainerType(String type) {
        return Set.of("group", "columns").contains(type);
    }

    /**
     * Validate form data against a schema
     * @param schema The form schema
     * @param formData The submitted form data
     * @throws FormValidationException if validation fails
     */
    public void validateFormData(JsonNode schema, Map<String, Object> formData) {
        Map<String, List<String>> errors = new HashMap<>();

        if (formData == null || formData.isEmpty()) {
            errors.put("formData", List.of("Form data cannot be empty"));
            throw new FormValidationException("Form data validation failed", errors);
        }

        // Extract field definitions from schema
        Map<String, JsonNode> fieldDefinitions = extractFieldDefinitions(schema);

        // Validate each field
        for (Map.Entry<String, JsonNode> entry : fieldDefinitions.entrySet()) {
            String fieldKey = entry.getKey();
            JsonNode fieldDef = entry.getValue();
            String type = fieldDef.has("type") ? fieldDef.get("type").asText() : "";

            // Skip display-only (B) and service-type (C) fields — no data expected for B;
            // C types that carry submitted data are rejected with 501 below.
            if (DISPLAY_TYPES.contains(type)) {
                continue;
            }
            if (SERVICE_TYPES.contains(type)) {
                if (formData.containsKey(fieldKey)) {
                    throw new FormFieldTypeNotImplementedException(type);
                }
                continue;
            }

            // Check required fields
            if (isRequired(fieldDef)) {
                if (!formData.containsKey(fieldKey) || formData.get(fieldKey) == null) {
                    addError(errors, fieldKey, "This field is required");
                    continue;
                }
            }

            // Validate field value if present
            if (formData.containsKey(fieldKey)) {
                Object value = formData.get(fieldKey);
                validateFieldValue(fieldKey, fieldDef, value, errors);
            }
        }

        if (!errors.isEmpty()) {
            throw new FormValidationException("Form data validation failed", errors);
        }

        log.info("Form data validation successful for {} fields", formData.size());
    }

    /**
     * Extract all field definitions from schema components
     */
    private Map<String, JsonNode> extractFieldDefinitions(JsonNode schema) {
        Map<String, JsonNode> fields = new HashMap<>();

        if (schema.has("components")) {
            extractFieldsRecursive(schema.get("components"), fields);
        }

        return fields;
    }

    /**
     * Recursively extract field definitions
     */
    private void extractFieldsRecursive(JsonNode components, Map<String, JsonNode> fields) {
        if (!components.isArray()) {
            return;
        }

        for (JsonNode component : components) {
            if (component.has("key")) {
                fields.put(component.get("key").asText(), component);
            }

            // Process nested components
            if (component.has("components")) {
                extractFieldsRecursive(component.get("components"), fields);
            }
        }
    }

    /**
     * Check if field is required
     */
    private boolean isRequired(JsonNode fieldDef) {
        if (!fieldDef.has("validate")) {
            return false;
        }

        JsonNode validate = fieldDef.get("validate");
        return validate.has("required") && validate.get("required").asBoolean();
    }

    /**
     * Validate individual field value
     */
    private void validateFieldValue(String fieldKey, JsonNode fieldDef, Object value,
                                     Map<String, List<String>> errors) {
        String type = fieldDef.get("type").asText();

        // Type-specific validation
        switch (type) {
            case "number":
                validateNumberField(fieldKey, fieldDef, value, errors);
                break;
            case "email":
                validateEmailField(fieldKey, value, errors);
                break;
            case "textfield":
            case "textarea":
                validateTextField(fieldKey, fieldDef, value, errors);
                break;
            case "select":
            case "radio":
                validateChoiceField(fieldKey, fieldDef, value, errors);
                break;
            case "checkbox":
                validateCheckboxField(fieldKey, value, errors);
                break;
            default:
                // Generic validation for other types
                break;
        }
    }

    /**
     * Validate number field
     */
    private void validateNumberField(String fieldKey, JsonNode fieldDef, Object value,
                                      Map<String, List<String>> errors) {
        if (!(value instanceof Number)) {
            try {
                Double.parseDouble(value.toString());
            } catch (NumberFormatException e) {
                addError(errors, fieldKey, "Must be a valid number");
                return;
            }
        }

        double numValue = value instanceof Number ? ((Number) value).doubleValue()
                                                  : Double.parseDouble(value.toString());

        // Check min/max if defined
        if (fieldDef.has("validate")) {
            JsonNode validate = fieldDef.get("validate");
            if (validate.has("min") && numValue < validate.get("min").asDouble()) {
                addError(errors, fieldKey, "Must be at least " + validate.get("min").asDouble());
            }
            if (validate.has("max") && numValue > validate.get("max").asDouble()) {
                addError(errors, fieldKey, "Must be at most " + validate.get("max").asDouble());
            }
        }
    }

    /**
     * Validate email field
     */
    private void validateEmailField(String fieldKey, Object value, Map<String, List<String>> errors) {
        String email = value.toString();
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            addError(errors, fieldKey, "Must be a valid email address");
        }
    }

    /**
     * Validate text field
     */
    private void validateTextField(String fieldKey, JsonNode fieldDef, Object value,
                                    Map<String, List<String>> errors) {
        String text = value.toString();

        if (fieldDef.has("validate")) {
            JsonNode validate = fieldDef.get("validate");

            // Check min length
            if (validate.has("minLength") && text.length() < validate.get("minLength").asInt()) {
                addError(errors, fieldKey, "Must be at least " + validate.get("minLength").asInt() + " characters");
            }

            // Check max length
            if (validate.has("maxLength") && text.length() > validate.get("maxLength").asInt()) {
                addError(errors, fieldKey, "Must be at most " + validate.get("maxLength").asInt() + " characters");
            }

            // Check pattern if defined
            if (validate.has("pattern")) {
                String pattern = validate.get("pattern").asText();
                if (!text.matches(pattern)) {
                    addError(errors, fieldKey, "Does not match required pattern");
                }
            }
        }
    }

    /**
     * Validate choice field (select, radio)
     */
    private void validateChoiceField(String fieldKey, JsonNode fieldDef, Object value,
                                      Map<String, List<String>> errors) {
        if (!fieldDef.has("values")) {
            return;
        }

        JsonNode values = fieldDef.get("values");
        if (!values.isArray()) {
            return;
        }

        List<String> validValues = new ArrayList<>();
        for (JsonNode val : values) {
            if (val.has("value")) {
                validValues.add(val.get("value").asText());
            }
        }

        if (!validValues.contains(value.toString())) {
            addError(errors, fieldKey, "Must be one of: " + String.join(", ", validValues));
        }
    }

    /**
     * Validate checkbox field
     */
    private void validateCheckboxField(String fieldKey, Object value, Map<String, List<String>> errors) {
        if (!(value instanceof Boolean)) {
            if (!"true".equalsIgnoreCase(value.toString()) && !"false".equalsIgnoreCase(value.toString())) {
                addError(errors, fieldKey, "Must be true or false");
            }
        }
    }

    /**
     * Add an error to the error map
     */
    private void addError(Map<String, List<String>> errors, String field, String message) {
        errors.computeIfAbsent(field, k -> new ArrayList<>()).add(message);
    }

    /**
     * Get validation errors from the last validation
     * @return Map of field names to error messages
     */
    public Map<String, List<String>> getValidationErrors() {
        // This is typically accessed from the exception
        return Map.of();
    }
}
