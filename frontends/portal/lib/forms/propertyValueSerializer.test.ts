import { describe, it, expect } from 'vitest'
import {
  serializePropertyValue,
  deserializePropertyValue,
  serializeSchemaProperties,
  deserializeSchemaProperties,
} from './propertyValueSerializer'

// ─── serializePropertyValue / deserializePropertyValue ──────────────────────

describe('serializePropertyValue', () => {
  it('round-trips object dataSource through serialize/deserialize', () => {
    const original = { url: 'https://api.example.com/data', method: 'GET', params: { page: 1 } }
    const serialized = serializePropertyValue(original)
    const deserialized = deserializePropertyValue(serialized)
    expect(deserialized).toEqual(original)
  })

  it('round-trips array dataSource through serialize/deserialize', () => {
    const original = [{ value: 'a', label: 'Option A' }, { value: 'b', label: 'Option B' }]
    const serialized = serializePropertyValue(original)
    const deserialized = deserializePropertyValue(serialized)
    expect(deserialized).toEqual(original)
  })

  it('returns empty string for null', () => {
    expect(serializePropertyValue(null)).toBe('')
  })

  it('returns empty string for undefined', () => {
    expect(serializePropertyValue(undefined)).toBe('')
  })

  it('converts number to string', () => {
    expect(serializePropertyValue(42)).toBe('42')
  })

  it('converts boolean to string', () => {
    expect(serializePropertyValue(true)).toBe('true')
  })

  it('returns string as-is', () => {
    expect(serializePropertyValue('hello')).toBe('hello')
  })
})

describe('deserializePropertyValue', () => {
  it('returns raw string if JSON.parse fails', () => {
    expect(deserializePropertyValue('not json')).toBe('not json')
  })

  it('returns empty string for blank input', () => {
    expect(deserializePropertyValue('')).toBe('')
    expect(deserializePropertyValue('   ')).toBe('')
  })

  it('keeps numeric string as string (no scalar promotion)', () => {
    expect(deserializePropertyValue('42')).toBe('42')
  })

  it('keeps boolean string as string (no scalar promotion)', () => {
    expect(deserializePropertyValue('true')).toBe('true')
  })

  it('parses a JSON string literal', () => {
    expect(deserializePropertyValue('"hello"')).toBe('"hello"')
  })
})

// ─── serializeSchemaProperties / deserializeSchemaProperties ────────────────

describe('serializeSchemaProperties', () => {
  it('serializes object property values to JSON strings', () => {
    const schema = {
      type: 'default',
      components: [
        {
          type: 'select',
          key: 'categoryId',
          properties: {
            dataSource: { url: '/api/cats', labelField: 'name', valueField: 'id' },
            label: 'simple string',
          },
        },
      ],
    }

    const result = serializeSchemaProperties(schema as Record<string, unknown>)
    const comp = (result.components as Record<string, unknown>[])[0]
    const props = comp.properties as Record<string, unknown>

    expect(typeof props.dataSource).toBe('string')
    expect(props.label).toBe('simple string')
  })

  it('returns schema unchanged when there are no components', () => {
    const schema = { type: 'default', components: [] }
    expect(serializeSchemaProperties(schema)).toEqual(schema)
  })
})

describe('deserializeSchemaProperties', () => {
  it('deserializes JSON string property values back to objects', () => {
    const schema = {
      type: 'default',
      components: [
        {
          type: 'select',
          key: 'categoryId',
          properties: {
            dataSource: JSON.stringify({ url: '/api/cats', labelField: 'name', valueField: 'id' }),
            label: 'simple string',
          },
        },
      ],
    }

    const result = deserializeSchemaProperties(schema as Record<string, unknown>)
    const comp = (result.components as Record<string, unknown>[])[0]
    const props = comp.properties as Record<string, unknown>

    expect(props.dataSource).toEqual({ url: '/api/cats', labelField: 'name', valueField: 'id' })
    expect(props.label).toBe('simple string')
  })

  it('leaves non-JSON strings as plain strings', () => {
    const schema = {
      type: 'default',
      components: [
        {
          type: 'textfield',
          key: 'name',
          properties: { placeholder: 'Enter name' },
        },
      ],
    }

    const result = deserializeSchemaProperties(schema as Record<string, unknown>)
    const comp = (result.components as Record<string, unknown>[])[0]
    const props = comp.properties as Record<string, unknown>

    expect(props.placeholder).toBe('Enter name')
  })
})

// ─── full round-trip through schema helpers ──────────────────────────────────

describe('schema round-trip', () => {
  it('preserves object property values through serialize/deserialize cycle', () => {
    const original = {
      type: 'default',
      components: [
        {
          type: 'select',
          key: 'categoryId',
          properties: {
            dataSource: { url: '/api/cats', labelField: 'name', valueField: 'id', filter: { active: true } },
            label: 'Category',
          },
        },
      ],
    }

    const serialized = serializeSchemaProperties(original as Record<string, unknown>)
    const restored = deserializeSchemaProperties(serialized)

    expect(restored).toEqual(original)
  })
})
