/**
 * Serializes a custom property value for display in a text input.
 * Object and array values are formatted as pretty-printed JSON strings.
 */
export function serializePropertyValue(value: unknown): string {
  if (value === null || value === undefined) return '';
  if (typeof value === 'object') return JSON.stringify(value, null, 2);
  return String(value);
}

/**
 * Deserializes a raw string from a text input back to a typed value.
 * If the string parses as a JSON object or array, returns the parsed value.
 * Otherwise returns the raw string.
 */
export function deserializePropertyValue(raw: string): unknown {
  const trimmed = raw.trim();
  if (!trimmed) return '';
  try {
    const parsed = JSON.parse(trimmed);
    if (typeof parsed === 'object' && parsed !== null) return parsed;
    // scalars stay as strings — don't promote "42" to number 42
    return raw;
  } catch {
    return raw;
  }
}

/**
 * Walks a form schema's component tree and serializes any object/array
 * custom property values to JSON strings so they render correctly in the
 * form-js-editor properties panel.
 */
export function serializeSchemaProperties(schema: Record<string, unknown>): Record<string, unknown> {
  if (!schema || typeof schema !== 'object') return schema;

  const components = schema.components;
  if (!Array.isArray(components)) return schema;

  return {
    ...schema,
    components: components.map((component: unknown) => {
      if (!component || typeof component !== 'object') return component;
      const comp = component as Record<string, unknown>;
      const properties = comp.properties;
      if (!properties || typeof properties !== 'object') return comp;

      const serializedProperties: Record<string, unknown> = {};
      for (const [key, value] of Object.entries(properties as Record<string, unknown>)) {
        serializedProperties[key] = (typeof value === 'object' && value !== null)
          ? serializePropertyValue(value)
          : value;
      }

      return { ...comp, properties: serializedProperties };
    }),
  };
}

/**
 * Walks a form schema's component tree and deserializes any JSON string
 * custom property values back to objects/arrays before persisting.
 */
export function deserializeSchemaProperties(schema: Record<string, unknown>): Record<string, unknown> {
  if (!schema || typeof schema !== 'object') return schema;

  const components = schema.components;
  if (!Array.isArray(components)) return schema;

  return {
    ...schema,
    components: components.map((component: unknown) => {
      if (!component || typeof component !== 'object') return component;
      const comp = component as Record<string, unknown>;
      const properties = comp.properties;
      if (!properties || typeof properties !== 'object') return comp;

      const deserializedProperties: Record<string, unknown> = {};
      for (const [key, value] of Object.entries(properties as Record<string, unknown>)) {
        deserializedProperties[key] = (typeof value === 'string')
          ? deserializePropertyValue(value)
          : value;
      }

      return { ...comp, properties: deserializedProperties };
    }),
  };
}
