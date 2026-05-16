// ---------------------------------------------------------------------------
// Shared helpers for EXTERNAL_API_CALL section and its sub-components.
// These are file-private utilities that were previously inlined in
// ServiceTaskPropertiesPanel.tsx. No external consumers.
// ---------------------------------------------------------------------------

export function extractFieldsFromJson(jsonString: string): Array<{ variable: string; path: string }> {
  try {
    const parsed = JSON.parse(jsonString)
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) return []
    return Object.keys(parsed).map(key => ({
      variable: key.replace(/_([a-z])/g, (_, c: string) => c.toUpperCase()),
      path: `$.${key}`,
    }))
  } catch {
    return []
  }
}

export function readExtractFields(element: any): Array<{ variable: string; path: string }> {
  const raw: string = element.businessObject.get('ab:extractFields') || ''
  if (!raw.trim()) return []
  return raw
    .split('\n')
    .map((line: string) => line.trim())
    .filter(Boolean)
    .map((line: string) => {
      const colonIdx = line.indexOf(':')
      if (colonIdx === -1) return { variable: line, path: '' }
      return { variable: line.slice(0, colonIdx), path: line.slice(colonIdx + 1) }
    })
}

export function writeExtractFields(
  element: any,
  modeler: any,
  rows: Array<{ variable: string; path: string }>
) {
  const modeling = modeler.get('modeling')
  const value = rows
    .filter(r => r.variable.trim())
    .map(r => `${r.variable}:${r.path}`)
    .join('\n')
  modeling.updateProperties(element, { 'ab:extractFields': value || undefined })
}

export function formatConnectorError(raw: string): string {
  if (!raw) return 'Failed to load connectors.'
  const technicalPattern = /^\(Field '.*?'\)\s+value is/i
  if (technicalPattern.test(raw)) {
    return 'Connector configuration is incomplete. Please select a valid connector.'
  }
  return raw
}
