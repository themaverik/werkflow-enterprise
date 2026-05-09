package com.werkflow.admin.designtime.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.werkflow.admin.designtime.connector.dto.FlatField;
import com.werkflow.admin.designtime.connector.service.SchemaFlattenerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaFlattenerServiceTest {

    private SchemaFlattenerService flattener;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        flattener = new SchemaFlattenerService();
        objectMapper = new ObjectMapper();
    }

    // -------------------------------------------------------------------------
    // Primitive leaf
    // -------------------------------------------------------------------------

    @Test
    void flatten_simpleObject_returnsTopLevelFields() throws Exception {
        JsonNode schema = objectMapper.readTree("""
            {
              "type": "object",
              "required": ["name"],
              "properties": {
                "name":  { "type": "string" },
                "age":   { "type": "integer" },
                "score": { "type": "number" }
              }
            }
            """);

        List<FlatField> fields = flattener.flatten(schema);

        assertThat(fields).hasSize(3);
        FlatField nameField = fields.stream().filter(f -> "name".equals(f.path())).findFirst().orElseThrow();
        assertThat(nameField.type()).isEqualTo("string");
        assertThat(nameField.required()).isTrue();
        assertThat(nameField.depth()).isEqualTo(0);

        FlatField ageField = fields.stream().filter(f -> "age".equals(f.path())).findFirst().orElseThrow();
        assertThat(ageField.required()).isFalse();
        assertThat(ageField.isArrayItem()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Nested object
    // -------------------------------------------------------------------------

    @Test
    void flatten_nestedObject_producesDotNotationPaths() throws Exception {
        JsonNode schema = objectMapper.readTree("""
            {
              "type": "object",
              "properties": {
                "address": {
                  "type": "object",
                  "properties": {
                    "street": { "type": "string" },
                    "city":   { "type": "string" }
                  }
                }
              }
            }
            """);

        List<FlatField> fields = flattener.flatten(schema);

        assertThat(fields).extracting(FlatField::path)
                .containsExactlyInAnyOrder("address.street", "address.city");
        fields.forEach(f -> assertThat(f.depth()).isEqualTo(1));
    }

    // -------------------------------------------------------------------------
    // Array of objects
    // -------------------------------------------------------------------------

    @Test
    void flatten_arrayOfObjects_producesArrayItemPaths() throws Exception {
        JsonNode schema = objectMapper.readTree("""
            {
              "type": "object",
              "properties": {
                "items": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "price": { "type": "number" },
                      "sku":   { "type": "string" }
                    }
                  }
                }
              }
            }
            """);

        List<FlatField> fields = flattener.flatten(schema);

        assertThat(fields).extracting(FlatField::path)
                .containsExactlyInAnyOrder("items[].price", "items[].sku");
        fields.forEach(f -> assertThat(f.isArrayItem()).isTrue());
    }

    // -------------------------------------------------------------------------
    // Array of primitives
    // -------------------------------------------------------------------------

    @Test
    void flatten_arrayOfPrimitives_emitsSingleArrayItemField() throws Exception {
        JsonNode schema = objectMapper.readTree("""
            {
              "type": "object",
              "properties": {
                "tags": { "type": "array", "items": { "type": "string" } }
              }
            }
            """);

        List<FlatField> fields = flattener.flatten(schema);

        assertThat(fields).hasSize(1);
        assertThat(fields.get(0).path()).isEqualTo("tags[]");
        assertThat(fields.get(0).type()).isEqualTo("string");
        assertThat(fields.get(0).isArrayItem()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Format hint propagation
    // -------------------------------------------------------------------------

    @Test
    void flatten_formatHint_propagatedToFlatField() throws Exception {
        JsonNode schema = objectMapper.readTree("""
            {
              "type": "object",
              "properties": {
                "createdAt": { "type": "string", "format": "date-time" },
                "id":        { "type": "string", "format": "uuid" }
              }
            }
            """);

        List<FlatField> fields = flattener.flatten(schema);

        FlatField createdAt = fields.stream().filter(f -> "createdAt".equals(f.path())).findFirst().orElseThrow();
        assertThat(createdAt.format()).isEqualTo("date-time");

        FlatField id = fields.stream().filter(f -> "id".equals(f.path())).findFirst().orElseThrow();
        assertThat(id.format()).isEqualTo("uuid");
    }

    // -------------------------------------------------------------------------
    // Empty schema
    // -------------------------------------------------------------------------

    @Test
    void flatten_emptyObjectSchema_returnsEmptyList() throws Exception {
        JsonNode schema = objectMapper.readTree("{}");
        List<FlatField> fields = flattener.flatten(schema);
        assertThat(fields).isEmpty();
    }

    @Test
    void flatten_nullLike_returnsEmptyList() throws Exception {
        JsonNode schema = objectMapper.readTree("{ \"type\": \"object\" }");
        List<FlatField> fields = flattener.flatten(schema);
        // No properties — emits the root as opaque object leaf; but path is empty so filtered
        assertThat(fields).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Deep nesting
    // -------------------------------------------------------------------------

    @Test
    void flatten_deeplyNested_depthTrackedCorrectly() throws Exception {
        JsonNode schema = objectMapper.readTree("""
            {
              "type": "object",
              "properties": {
                "level1": {
                  "type": "object",
                  "properties": {
                    "level2": {
                      "type": "object",
                      "properties": {
                        "value": { "type": "string" }
                      }
                    }
                  }
                }
              }
            }
            """);

        List<FlatField> fields = flattener.flatten(schema);
        assertThat(fields).hasSize(1);
        assertThat(fields.get(0).path()).isEqualTo("level1.level2.value");
        assertThat(fields.get(0).depth()).isEqualTo(2);
    }
}
