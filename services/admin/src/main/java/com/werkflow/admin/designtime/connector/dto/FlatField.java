package com.werkflow.admin.designtime.connector.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A single flattened field descriptor produced by {@link com.werkflow.admin.designtime.connector.service.SchemaFlattenerService}.
 *
 * <p>Example: given an output schema with a nested array of items each having a {@code price}
 * field, the flattener emits {@code path="items[].price", type="number", isArrayItem=true, depth=2}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FlatField(
        /** Dot-notation path with {@code []} suffix for array items. e.g. {@code order.items[].price}. */
        String path,
        /** JSON Schema primitive type: string, number, integer, boolean, object, array, or null. */
        String type,
        /** JSON Schema format hint if present (e.g. "date-time", "uuid", "email"). */
        String format,
        /** Whether this field is inside an array item traversal. */
        boolean isArrayItem,
        /** Whether the field appears in the schema's {@code required} list. */
        boolean required,
        /** Nesting depth from the schema root (0 = top-level). */
        int depth
) {}
