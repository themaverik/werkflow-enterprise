package com.werkflow.admin.designtime.connector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Resolves internal {@code $ref}s within a ConnectorDefinition JSON document.
 *
 * <p>Supports only JSON Pointer-style {@code $ref}s that point within the same document
 * (i.e. {@code #/$defs/Foo} or {@code #/spec/components/schemas/Bar}).
 * External URL references are not resolved at this layer.</p>
 *
 * <p>The resolved output is a self-contained JSON Schema document with no
 * unresolvable {@code $ref}s, safe to hand to the designer or flattener.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaResolverService {

    private final ObjectMapper objectMapper;

    /**
     * Resolves all internal {@code $ref}s in the given JSON Schema node
     * using definitions from the connector definition's {@code spec.components.schemas}
     * and {@code $defs} sections.
     *
     * @param schemaNode     the schema to resolve (modified in-place on a deep copy)
     * @param definitionJson the full ConnectorDefinition JSON string to extract {@code $defs} from
     * @return a new {@link JsonNode} with {@code $ref}s replaced by their target schemas
     */
    public JsonNode resolve(JsonNode schemaNode, String definitionJson) {
        Map<String, JsonNode> definitions = extractDefinitions(definitionJson);
        JsonNode copy = schemaNode.deepCopy();
        return resolveNode(copy, definitions, 0);
    }

    // -------------------------------------------------------------------------
    // Internal resolution
    // -------------------------------------------------------------------------

    private Map<String, JsonNode> extractDefinitions(String definitionJson) {
        Map<String, JsonNode> defs = new HashMap<>();
        try {
            JsonNode root = objectMapper.readTree(definitionJson);
            collectDefs(root.path("$defs"), "#/$defs/", defs);
            collectDefs(root.path("spec").path("components").path("schemas"),
                        "#/spec/components/schemas/", defs);
        } catch (IOException e) {
            log.warn("SchemaResolverService: could not parse definition JSON — {}", e.getMessage());
        }
        return defs;
    }

    private void collectDefs(JsonNode defsNode, String prefix, Map<String, JsonNode> target) {
        if (defsNode.isMissingNode() || !defsNode.isObject()) return;
        defsNode.fields().forEachRemaining(entry ->
            target.put(prefix + entry.getKey(), entry.getValue()));
    }

    /**
     * Recursively resolves {@code $ref}s in the node tree.
     * Cycles are guarded by a depth limit; deeply nested schemas are unusual in practice.
     */
    private JsonNode resolveNode(JsonNode node, Map<String, JsonNode> defs, int depth) {
        if (depth > 20) {
            log.warn("SchemaResolverService: max recursion depth reached — possible cycle");
            return node;
        }

        if (!node.isObject()) return node;

        ObjectNode obj = (ObjectNode) node;
        JsonNode refNode = obj.get("$ref");

        if (refNode != null && refNode.isTextual()) {
            String ref = refNode.asText();
            JsonNode resolved = defs.get(ref);
            if (resolved != null) {
                // Replace this node's content with the resolved schema
                ObjectNode replacement = (ObjectNode) resolved.deepCopy();
                obj.remove("$ref");
                Iterator<Map.Entry<String, JsonNode>> fields = replacement.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> f = fields.next();
                    obj.set(f.getKey(), f.getValue());
                }
                return resolveNode(obj, defs, depth + 1);
            } else {
                log.debug("SchemaResolverService: unresolvable $ref '{}' — left as-is", ref);
                return obj;
            }
        }

        // Recurse into all child fields
        obj.fields().forEachRemaining(entry -> {
            JsonNode resolved = resolveNode(entry.getValue(), defs, depth + 1);
            if (resolved != entry.getValue()) {
                obj.set(entry.getKey(), resolved);
            }
        });
        return obj;
    }
}
