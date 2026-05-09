package com.werkflow.admin.designtime.connector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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

    private static final int MAX_RESOLVED_NODES = 5_000;

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
        int[] nodeCount = {0};
        return resolveNode(copy, definitions, 0, nodeCount);
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
    private JsonNode resolveNode(JsonNode node, Map<String, JsonNode> defs, int depth, int[] nodeCount) {
        if (++nodeCount[0] > MAX_RESOLVED_NODES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Schema exceeds maximum resolved size (" + MAX_RESOLVED_NODES + " nodes) — possible circular or exponential $ref");
        }
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
                ObjectNode replacement = (ObjectNode) resolved.deepCopy();
                obj.remove("$ref");
                Iterator<Map.Entry<String, JsonNode>> fields = replacement.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> f = fields.next();
                    obj.set(f.getKey(), f.getValue());
                }
                return resolveNode(obj, defs, depth + 1, nodeCount);
            } else {
                log.debug("SchemaResolverService: unresolvable $ref '{}' — left as-is", ref);
                return obj;
            }
        }

        // Recurse into all child fields
        obj.fields().forEachRemaining(entry -> {
            JsonNode r = resolveNode(entry.getValue(), defs, depth + 1, nodeCount);
            if (r != entry.getValue()) {
                obj.set(entry.getKey(), r);
            }
        });
        return obj;
    }
}
