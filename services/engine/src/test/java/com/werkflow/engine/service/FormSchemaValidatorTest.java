package com.werkflow.engine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.werkflow.engine.exception.FormFieldTypeNotImplementedException;
import com.werkflow.engine.exception.FormValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormSchemaValidatorTest {

    private FormSchemaValidator validator;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        validator = new FormSchemaValidator();
    }

    // --- field type category constants ---

    @Test
    void variableTypes_containsExpectedTypes() {
        assertThat(FormSchemaValidator.VARIABLE_TYPES).contains(
                "textfield", "number", "textarea", "checkbox", "radio",
                "select", "date", "time", "datetime", "email",
                "checklist", "taglist", "group", "columns"
        );
    }

    @Test
    void displayTypes_containsExpectedTypes() {
        assertThat(FormSchemaValidator.DISPLAY_TYPES).containsExactlyInAnyOrder(
                "html", "text", "button", "image", "spacer", "separator"
        );
    }

    @Test
    void serviceTypes_containsDynamiclist() {
        assertThat(FormSchemaValidator.SERVICE_TYPES).contains("dynamiclist");
    }

    // --- validateFormSchema ---

    @Test
    void validateFormSchema_acceptsVariableTypeFields() throws Exception {
        JsonNode schema = mapper.readTree("""
            {"type":"default","components":[
                {"type":"textfield","key":"name"},
                {"type":"number","key":"amount"},
                {"type":"select","key":"dept"}
            ]}""");
        assertThatNoException().isThrownBy(() -> validator.validateFormSchema(schema));
    }

    @Test
    void validateFormSchema_acceptsDisplayTypeFields() throws Exception {
        JsonNode schema = mapper.readTree("""
            {"type":"default","components":[
                {"type":"html"},
                {"type":"spacer"},
                {"type":"button"}
            ]}""");
        assertThatNoException().isThrownBy(() -> validator.validateFormSchema(schema));
    }

    @Test
    void validateFormSchema_acceptsServiceTypeFields() throws Exception {
        JsonNode schema = mapper.readTree("""
            {"type":"default","components":[
                {"type":"dynamiclist","key":"items"}
            ]}""");
        assertThatNoException().isThrownBy(() -> validator.validateFormSchema(schema));
    }

    @Test
    void validateFormSchema_rejectsUnknownFieldType() throws Exception {
        JsonNode schema = mapper.readTree("""
            {"type":"default","components":[
                {"type":"customwidget","key":"x"}
            ]}""");
        assertThatThrownBy(() -> validator.validateFormSchema(schema))
                .isInstanceOf(FormValidationException.class)
                .hasMessageContaining("validation failed");
    }

    @Test
    void validateFormSchema_rejectsNullSchema() {
        assertThatThrownBy(() -> validator.validateFormSchema(null))
                .isInstanceOf(FormValidationException.class);
    }

    // --- validateFormData ---

    @Test
    void validateFormData_skipsDisplayTypeFields_noValidationRequired() throws Exception {
        JsonNode schema = mapper.readTree("""
            {"type":"default","components":[
                {"type":"textfield","key":"name"},
                {"type":"html"}
            ]}""");
        Map<String, Object> data = Map.of("name", "Alice");
        assertThatNoException().isThrownBy(() -> validator.validateFormData(schema, data));
    }

    @Test
    void validateFormData_throwsNotImplemented_whenServiceTypeKeyInData() throws Exception {
        JsonNode schema = mapper.readTree("""
            {"type":"default","components":[
                {"type":"dynamiclist","key":"items"}
            ]}""");
        Map<String, Object> data = Map.of("items", "[{\"id\":1}]");
        assertThatThrownBy(() -> validator.validateFormData(schema, data))
                .isInstanceOf(FormFieldTypeNotImplementedException.class)
                .hasMessageContaining("dynamiclist");
    }

    @Test
    void validateFormData_skipsServiceTypeKey_whenNotInSubmittedData() throws Exception {
        JsonNode schema = mapper.readTree("""
            {"type":"default","components":[
                {"type":"textfield","key":"name"},
                {"type":"dynamiclist","key":"items"}
            ]}""");
        Map<String, Object> data = Map.of("name", "Bob");
        assertThatNoException().isThrownBy(() -> validator.validateFormData(schema, data));
    }

    // --- extractDisplayOnlyKeys ---

    @Test
    void extractDisplayOnlyKeys_returnsOnlyDisplayTypeKeys() throws Exception {
        JsonNode schema = mapper.readTree("""
            {"type":"default","components":[
                {"type":"textfield","key":"name"},
                {"type":"html","key":"banner"},
                {"type":"button","key":"submit"},
                {"type":"spacer","key":"gap"}
            ]}""");
        Set<String> displayKeys = validator.extractDisplayOnlyKeys(schema);
        assertThat(displayKeys).containsExactlyInAnyOrder("banner", "submit", "gap");
        assertThat(displayKeys).doesNotContain("name");
    }

    @Test
    void extractDisplayOnlyKeys_returnsEmptySet_whenNoDisplayTypes() throws Exception {
        JsonNode schema = mapper.readTree("""
            {"type":"default","components":[
                {"type":"textfield","key":"amount"}
            ]}""");
        assertThat(validator.extractDisplayOnlyKeys(schema)).isEmpty();
    }
}
