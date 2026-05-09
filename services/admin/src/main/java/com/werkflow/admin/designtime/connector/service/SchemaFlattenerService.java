package com.werkflow.admin.designtime.connector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.werkflow.admin.designtime.connector.dto.FlatField;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * Converts a resolved JSON Schema into a flat list of {@link FlatField} descriptors.
 *
 * <h3>Flattening rules</h3>
 * <ul>
 *   <li>Object properties are traversed depth-first; path segments are joined with {@code .}.</li>
 *   <li>Array item schemas are emitted with an {@code []} suffix on the array segment.
 *       e.g. schema {@code {type:array, items:{type:object, properties:{price:{type:number}}}}}
 *       produces {@code items[].price}.</li>
 *   <li>Primitive leaves (string, number, integer, boolean) are emitted as fields.</li>
 *   <li>Object nodes without properties are emitted with type "object" so callers know
 *       the field exists but its inner shape is unknown.</li>
 *   <li>Max depth is capped at 10 to prevent runaway traversal on pathological schemas.</li>
 *   <li>Required fields are detected from the nearest enclosing object's {@code required} array.</li>
 * </ul>
 */
@Slf4j
@Service
public class SchemaFlattenerService {

    private static final int MAX_DEPTH = 10;

    /**
     * Flattens the given resolved JSON Schema node into a list of {@link FlatField}s.
     *
     * @param schemaNode resolved JSON Schema (no {@code $ref}s)
     * @return ordered list of flat fields; never null
     */
    public List<FlatField> flatten(JsonNode schemaNode) {
        List<FlatField> result = new ArrayList<>();
        traverseNode(schemaNode, "", false, Set.of(), 0, result);
        return List.copyOf(result);
    }

    // -------------------------------------------------------------------------
    // Recursive traversal
    // -------------------------------------------------------------------------

    private void traverseNode(
            JsonNode node,
            String path,
            boolean isArrayItem,
            Set<String> requiredFields,
            int depth,
            List<FlatField> accumulator) {

        if (depth > MAX_DEPTH || node == null || node.isMissingNode()) return;

        String type = typeOf(node);

        switch (type) {
            case "object" -> traverseObject(node, path, isArrayItem, depth, accumulator);
            case "array"  -> traverseArray(node, path, isArrayItem, depth, accumulator);
            default -> {
                // Primitive leaf — emit if path is non-empty
                if (!path.isEmpty()) {
                    boolean required = requiredFields.contains(lastSegment(path));
                    accumulator.add(new FlatField(
                            path, type, formatOf(node), isArrayItem, required, depth - 1));
                }
            }
        }
    }

    private void traverseObject(
            JsonNode node, String path, boolean isArrayItem, int depth, List<FlatField> acc) {

        // Emit the object itself if it has no properties (opaque object leaf)
        JsonNode props = node.path("properties");
        if (!props.isObject() || props.isEmpty()) {
            if (!path.isEmpty()) {
                acc.add(new FlatField(path, "object", null, isArrayItem, false, depth - 1));
            }
            return;
        }

        // Collect required set for this object level
        Set<String> required = buildRequiredSet(node);

        props.fields().forEachRemaining(entry -> {
            String childPath = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
            boolean childRequired = required.contains(entry.getKey());
            String childType = typeOf(entry.getValue());

            if ("object".equals(childType) || "array".equals(childType)) {
                traverseNode(entry.getValue(), childPath, isArrayItem, required, depth + 1, acc);
            } else {
                // Leaf
                acc.add(new FlatField(
                        childPath, childType, formatOf(entry.getValue()),
                        isArrayItem, childRequired, depth));
            }
        });
    }

    private void traverseArray(
            JsonNode node, String path, boolean parentIsArray, int depth, List<FlatField> acc) {

        JsonNode items = node.path("items");
        String arrayPath = path.isEmpty() ? "[]" : path + "[]";

        if (items.isMissingNode()) {
            // Untyped array — emit as-is
            if (!path.isEmpty()) {
                acc.add(new FlatField(path, "array", null, parentIsArray, false, depth - 1));
            }
            return;
        }

        String itemsType = typeOf(items);
        if ("object".equals(itemsType)) {
            traverseObject(items, arrayPath, true, depth + 1, acc);
        } else if ("array".equals(itemsType)) {
            traverseArray(items, arrayPath, true, depth + 1, acc);
        } else {
            // Primitive array items
            acc.add(new FlatField(arrayPath, itemsType, formatOf(items), true, false, depth));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String typeOf(JsonNode node) {
        JsonNode typeNode = node.path("type");
        if (typeNode.isTextual()) return typeNode.asText();
        // Infer from structure when type is absent
        if (node.has("properties")) return "object";
        if (node.has("items")) return "array";
        return "object"; // fallback
    }

    private static String formatOf(JsonNode node) {
        JsonNode fmt = node.path("format");
        return fmt.isTextual() ? fmt.asText() : null;
    }

    private static Set<String> buildRequiredSet(JsonNode node) {
        JsonNode reqArray = node.path("required");
        if (!reqArray.isArray()) return Set.of();
        Set<String> set = new HashSet<>();
        reqArray.forEach(n -> {
            if (n.isTextual()) set.add(n.asText());
        });
        return set;
    }

    private static String lastSegment(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }
}
