package com.werkflow.admin.designtime.bpmn.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.werkflow.admin.designtime.bpmn.dto.VariableAtActivityResponse.ProcessVariableEntry;
import com.werkflow.admin.designtime.bpmn.service.ProcessVariableScopeService.FormRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the form-field extraction additions in {@link ProcessVariableScopeService}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Schema component parsing (flat, nested, type mapping)</li>
 *   <li>resolveFormFields happy path (object schemaJson)</li>
 *   <li>resolveFormFields with string-encoded schemaJson</li>
 *   <li>Resilience: 404 / engine error does not throw</li>
 *   <li>De-duplication: connector variable wins over form field of same name</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ProcessVariableScopeServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ProcessVariableScopeService service;

    @BeforeEach
    void setUp() {
        service = new ProcessVariableScopeService(restTemplate, objectMapper);
    }

    // -------------------------------------------------------------------------
    // extractFormFieldsFromSchema — pure unit tests, no HTTP
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("extractFormFieldsFromSchema")
    class ExtractFormFieldsTests {

        @Test
        @DisplayName("extracts flat field keys and maps types correctly")
        void flatFields_extractedWithCorrectTypes() throws Exception {
            String schemaJson = """
                    {
                      "components": [
                        { "type": "textfield", "key": "applicantName" },
                        { "type": "number",    "key": "requestedAmount" },
                        { "type": "datetime",  "key": "submittedOn" },
                        { "type": "date",      "key": "dueDate" },
                        { "type": "select",    "key": "department" }
                      ]
                    }
                    """;
            JsonNode schema = objectMapper.readTree(schemaJson);
            Map<String, ProcessVariableEntry> out = new LinkedHashMap<>();

            service.extractFormFieldsFromSchema(schema, "startEvent1", "Start", out);

            assertThat(out).containsKeys("applicantName", "requestedAmount", "submittedOn", "dueDate", "department");
            assertThat(out.get("applicantName").type()).isEqualTo("string");
            assertThat(out.get("requestedAmount").type()).isEqualTo("number");
            assertThat(out.get("submittedOn").type()).isEqualTo("date");
            assertThat(out.get("dueDate").type()).isEqualTo("date");
            assertThat(out.get("department").type()).isEqualTo("string");
        }

        @Test
        @DisplayName("recurses into nested layout components and extracts inner keys")
        void nestedLayoutComponents_innerKeysExtracted() throws Exception {
            String schemaJson = """
                    {
                      "components": [
                        {
                          "type": "group",
                          "components": [
                            { "type": "textfield", "key": "innerField" },
                            {
                              "type": "columns",
                              "components": [
                                { "type": "number", "key": "deeplyNested" }
                              ]
                            }
                          ]
                        }
                      ]
                    }
                    """;
            JsonNode schema = objectMapper.readTree(schemaJson);
            Map<String, ProcessVariableEntry> out = new LinkedHashMap<>();

            service.extractFormFieldsFromSchema(schema, "userTask1", "Review Task", out);

            assertThat(out).containsKeys("innerField", "deeplyNested");
            assertThat(out.get("deeplyNested").type()).isEqualTo("number");
            assertThat(out.get("innerField").setByActivity()).isEqualTo("userTask1");
            assertThat(out.get("innerField").setByTask()).isEqualTo("Review Task");
        }

        @Test
        @DisplayName("skips components without a key")
        void componentsWithoutKey_skipped() throws Exception {
            String schemaJson = """
                    {
                      "components": [
                        { "type": "text", "label": "Instructions" },
                        { "type": "textfield", "key": "validField" }
                      ]
                    }
                    """;
            JsonNode schema = objectMapper.readTree(schemaJson);
            Map<String, ProcessVariableEntry> out = new LinkedHashMap<>();

            service.extractFormFieldsFromSchema(schema, "start1", "Start", out);

            assertThat(out).containsOnlyKeys("validField");
        }

        @Test
        @DisplayName("does not overwrite an existing entry (putIfAbsent semantics)")
        void existingEntry_notOverwritten() throws Exception {
            String schemaJson = """
                    {
                      "components": [
                        { "type": "textfield", "key": "applicantName" }
                      ]
                    }
                    """;
            JsonNode schema = objectMapper.readTree(schemaJson);
            Map<String, ProcessVariableEntry> out = new LinkedHashMap<>();
            // pre-populate with a connector variable of the same name
            out.put("applicantName", new ProcessVariableEntry("applicantName", null, "connectorTask", "Connector"));

            service.extractFormFieldsFromSchema(schema, "startEvent1", "Start", out);

            assertThat(out.get("applicantName").setByActivity()).isEqualTo("connectorTask");
        }

        @Test
        @DisplayName("handles null or non-object schema gracefully")
        void nullSchema_noException() {
            Map<String, ProcessVariableEntry> out = new LinkedHashMap<>();
            service.extractFormFieldsFromSchema(null, "start1", "Start", out);
            assertThat(out).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // resolveFormFields — mocked HTTP
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("resolveFormFields")
    class ResolveFormFieldsTests {

        @Test
        @DisplayName("adds form fields when engine returns schemaJson as object node")
        void happyPath_objectSchemaJson_addsFields() throws Exception {
            String responseBody = """
                    {
                      "formKey": "leave-request-form",
                      "schemaJson": {
                        "components": [
                          { "type": "textfield", "key": "leaveType" },
                          { "type": "datetime",  "key": "startDate" }
                        ]
                      }
                    }
                    """;
            JsonNode responseNode = objectMapper.readTree(responseBody);
            when(restTemplate.exchange(contains("leave-request-form"), any(), any(), eq(JsonNode.class)))
                    .thenReturn(ResponseEntity.ok(responseNode));

            Map<String, ProcessVariableEntry> variables = new LinkedHashMap<>();
            List<FormRef> refs = List.of(new FormRef("leave-request-form", "startEvent1", "Start"));

            service.resolveFormFields(refs, "Bearer tok", variables);

            assertThat(variables).containsKeys("leaveType", "startDate");
            assertThat(variables.get("leaveType").type()).isEqualTo("string");
            assertThat(variables.get("startDate").type()).isEqualTo("date");
        }

        @Test
        @DisplayName("adds form fields when schemaJson is a JSON string (text node)")
        void happyPath_stringSchemaJson_parsedAndAddsFields() throws Exception {
            String innerSchema = "{\"components\":[{\"type\":\"number\",\"key\":\"amount\"}]}";
            String responseBody = "{\"formKey\":\"invoice-form\",\"schemaJson\":" +
                    objectMapper.writeValueAsString(innerSchema) + "}";
            JsonNode responseNode = objectMapper.readTree(responseBody);
            when(restTemplate.exchange(contains("invoice-form"), any(), any(), eq(JsonNode.class)))
                    .thenReturn(ResponseEntity.ok(responseNode));

            Map<String, ProcessVariableEntry> variables = new LinkedHashMap<>();
            List<FormRef> refs = List.of(new FormRef("invoice-form", "task1", "Invoice Task"));

            service.resolveFormFields(refs, "tok", variables);

            assertThat(variables).containsKey("amount");
            assertThat(variables.get("amount").type()).isEqualTo("number");
        }

        @Test
        @DisplayName("skips form and does not throw when engine returns 404")
        void engineReturns404_skipped() {
            when(restTemplate.exchange(any(String.class), any(), any(), eq(JsonNode.class)))
                    .thenThrow(HttpClientErrorException.create(
                            org.springframework.http.HttpStatus.NOT_FOUND, "Not Found",
                            org.springframework.http.HttpHeaders.EMPTY, new byte[0], null));

            Map<String, ProcessVariableEntry> variables = new LinkedHashMap<>();
            List<FormRef> refs = List.of(new FormRef("missing-form", "start1", "Start"));

            service.resolveFormFields(refs, "tok", variables);

            assertThat(variables).isEmpty();
        }

        @Test
        @DisplayName("skips form and does not throw when engine is unreachable")
        void engineUnreachable_skipped() {
            when(restTemplate.exchange(any(String.class), any(), any(), eq(JsonNode.class)))
                    .thenThrow(new org.springframework.web.client.ResourceAccessException("Connection refused"));

            Map<String, ProcessVariableEntry> variables = new LinkedHashMap<>();
            List<FormRef> refs = List.of(new FormRef("my-form", "start1", "Start"));

            service.resolveFormFields(refs, "tok", variables);

            assertThat(variables).isEmpty();
        }

        @Test
        @DisplayName("skips null body without throwing")
        void nullBody_skipped() {
            when(restTemplate.exchange(any(String.class), any(), any(), eq(JsonNode.class)))
                    .thenReturn(ResponseEntity.ok(null));

            Map<String, ProcessVariableEntry> variables = new LinkedHashMap<>();
            List<FormRef> refs = List.of(new FormRef("my-form", "start1", "Start"));

            service.resolveFormFields(refs, "tok", variables);

            assertThat(variables).isEmpty();
        }

        @Test
        @DisplayName("strips @version suffix from formKey before fetching")
        void versionedFormKey_suffixStripped() throws Exception {
            // FormRef.bareKey is already stripped by collectFormRef — verify URL uses bare key
            String responseBody = """
                    {
                      "schemaJson": { "components": [{ "type": "textfield", "key": "field1" }] }
                    }
                    """;
            JsonNode responseNode = objectMapper.readTree(responseBody);
            when(restTemplate.exchange(contains("/api/v1/form-schemas/leave-form"), any(), any(), eq(JsonNode.class)))
                    .thenReturn(ResponseEntity.ok(responseNode));

            Map<String, ProcessVariableEntry> variables = new LinkedHashMap<>();
            // bareKey already stripped (as collectFormRef would produce)
            List<FormRef> refs = List.of(new FormRef("leave-form", "start1", "Start"));

            service.resolveFormFields(refs, "tok", variables);

            assertThat(variables).containsKey("field1");
            // ensure no call with the @N suffix
            verify(restTemplate, never()).exchange(contains("leave-form@"), any(), any(), eq(JsonNode.class));
        }

        @Test
        @DisplayName("processes multiple form refs independently")
        void multipleFormRefs_allProcessed() throws Exception {
            JsonNode startFormResponse = objectMapper.readTree("""
                    {"schemaJson":{"components":[{"type":"textfield","key":"applicantName"}]}}
                    """);
            JsonNode taskFormResponse = objectMapper.readTree("""
                    {"schemaJson":{"components":[{"type":"number","key":"approvedAmount"}]}}
                    """);
            when(restTemplate.exchange(contains("start-form"), any(), any(), eq(JsonNode.class)))
                    .thenReturn(ResponseEntity.ok(startFormResponse));
            when(restTemplate.exchange(contains("approval-form"), any(), any(), eq(JsonNode.class)))
                    .thenReturn(ResponseEntity.ok(taskFormResponse));

            Map<String, ProcessVariableEntry> variables = new LinkedHashMap<>();
            List<FormRef> refs = List.of(
                    new FormRef("start-form", "startEvent1", "Start"),
                    new FormRef("approval-form", "approvalTask", "Approval Task")
            );

            service.resolveFormFields(refs, "tok", variables);

            assertThat(variables).containsKeys("applicantName", "approvedAmount");
            assertThat(variables.get("applicantName").setByActivity()).isEqualTo("startEvent1");
            assertThat(variables.get("approvedAmount").setByActivity()).isEqualTo("approvalTask");
        }
    }
}
