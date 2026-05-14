package com.werkflow.admin.designtime.dmn.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.werkflow.admin.designtime.bpmn.dto.VariableAtActivityResponse;
import com.werkflow.admin.designtime.bpmn.dto.VariableAtActivityResponse.ProcessVariableEntry;
import com.werkflow.admin.designtime.bpmn.service.ProcessVariableScopeService;
import com.werkflow.admin.designtime.platform.client.EngineClient;
import com.werkflow.admin.security.JwtClaimsExtractor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DTDS DMN Facade — endpoints consumed by the dmn-js editor for input column
 * type discovery and process variable binding candidate ranking.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/design/dmn")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('WORKFLOW_ADMIN','ADMIN','SUPER_ADMIN')")
@Tag(name = "DTDS — DMN Facade", description = "Design-time data service for the dmn-js editor")
public class DmnFacadeController {

    private final ProcessVariableScopeService variableScopeService;
    private final EngineClient engineClient;
    private final JwtClaimsExtractor jwtClaimsExtractor;
    private final ObjectMapper objectMapper;

    /**
     * Returns the input columns of a deployed DMN decision table with their FEEL types.
     * The dmn-js editor uses this to label input columns correctly.
     *
     * @param dmnId key of the deployed DMN decision (e.g. "procurementApproval")
     */
    @GetMapping("/decisions/{dmnId}/inputs")
    @Operation(summary = "Get DMN input columns",
               description = "Returns the input columns of a deployed DMN decision table with their FEEL type labels.")
    public ResponseEntity<List<Map<String, String>>> getDecisionInputs(
            @PathVariable String dmnId,
            @AuthenticationPrincipal Jwt jwt) {
        String tenantId = jwtClaimsExtractor.getTenantId(jwt);

        String dmnXml = engineClient.getDmnDefinitionXml(tenantId, dmnId);
        if (dmnXml == null || dmnXml.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        List<Map<String, String>> inputs = parseDmnInputColumns(dmnXml);
        return ResponseEntity.ok(inputs);
    }

    /**
     * Returns process variables that are type-compatible with the input columns of the given DMN,
     * ranked by compatibility score. The dmn-js editor uses this to suggest variable bindings.
     *
     * @param processDefId deployed process definition ID
     * @param activityId   BPMN activity ID where the DMN is evaluated
     * @param dmnId        key of the deployed DMN decision
     */
    @GetMapping("/binding-candidates")
    @Operation(summary = "Get DMN binding candidates",
               description = "Returns process variables ranked by type-compatibility with each DMN input column.")
    public ResponseEntity<List<Map<String, Object>>> getBindingCandidates(
            @RequestParam String processDefId,
            @RequestParam String activityId,
            @RequestParam String dmnId,
            @AuthenticationPrincipal Jwt jwt) {
        String tenantId = jwtClaimsExtractor.getTenantId(jwt);

        VariableAtActivityResponse scope = variableScopeService.variablesAt(
                tenantId, processDefId, activityId, jwt.getTokenValue());

        String dmnXml = engineClient.getDmnDefinitionXml(tenantId, dmnId);
        List<Map<String, String>> inputs = dmnXml != null ? parseDmnInputColumns(dmnXml) : List.of();

        List<Map<String, Object>> candidates = inputs.stream().map(input -> {
            String inputFeelType = input.getOrDefault("feelType", "string");
            List<Map<String, Object>> ranked = scope.variables().stream()
                    .sorted((a, b) -> compatScore(b, inputFeelType) - compatScore(a, inputFeelType))
                    .map(v -> Map.<String, Object>of(
                            "name",           v.name(),
                            "type",           v.type() != null ? v.type() : "unknown",
                            "setByActivity",  v.setByActivity() != null ? v.setByActivity() : "",
                            "compatible",     compatScore(v, inputFeelType) > 0))
                    .toList();
            return Map.<String, Object>of(
                    "inputId",    input.getOrDefault("id", ""),
                    "inputLabel", input.getOrDefault("label", ""),
                    "feelType",   inputFeelType,
                    "candidates", ranked);
        }).toList();

        return ResponseEntity.ok(candidates);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private List<Map<String, String>> parseDmnInputColumns(String dmnXml) {
        List<Map<String, String>> inputs = new ArrayList<>();
        try {
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new InputSource(new StringReader(dmnXml)));
            NodeList inputNodes = doc.getElementsByTagNameNS("https://www.omg.org/spec/DMN/20191111/MODEL/", "input");
            if (inputNodes.getLength() == 0) {
                inputNodes = doc.getElementsByTagName("input");
            }
            for (int i = 0; i < inputNodes.getLength(); i++) {
                Element el = (Element) inputNodes.item(i);
                String id    = el.getAttribute("id");
                String label = el.getAttribute("label");
                // inputExpression child carries typeRef
                NodeList exprs = el.getElementsByTagName("inputExpression");
                String typeRef = exprs.getLength() > 0
                        ? ((Element) exprs.item(0)).getAttribute("typeRef")
                        : "string";
                inputs.add(Map.of("id", id, "label", label, "feelType", typeRef.isBlank() ? "string" : typeRef));
            }
        } catch (Exception e) {
            log.warn("DmnFacadeController: failed to parse DMN XML for inputs: {}", e.getMessage());
        }
        return inputs;
    }

    private int compatScore(ProcessVariableEntry variable, String feelType) {
        if (variable.type() == null) return 0;
        return switch (feelType) {
            case "number", "integer" -> variable.type().matches("number|integer") ? 2 : 0;
            case "boolean"           -> "boolean".equals(variable.type()) ? 2 : 0;
            case "date", "date and time" -> "string".equals(variable.type()) ? 1 : 0;
            default                  -> "string".equals(variable.type()) ? 1 : 0;
        };
    }
}
