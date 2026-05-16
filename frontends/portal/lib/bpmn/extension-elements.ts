/**
 * Low-level read/write helpers for Flowable extension elements on BPMN business objects.
 *
 * These are the building blocks for both the bpmn-js properties panel adapter
 * (flowable-properties-provider.ts) and the React sidebar action sections
 * (components/bpmn/sections/*). Keeping them here breaks the import cycle that
 * would otherwise exist between those two consumers.
 *
 * All writes route through modeling.updateProperties to preserve bpmn-js
 * commandStack integrity (undo/redo, hasChanges).
 */

export function readFlowableField(element: any, fieldName: string): string {
  const ext = element.businessObject.extensionElements
  if (!ext) return ''
  const field = ext.get('values')?.find(
    (v: any) => v.$type === 'flowable:Field' && v.name === fieldName)
  return field?.expression ?? field?.string ?? ''
}

export function writeFlowableField(element: any, modeling: any, fieldName: string, value: string) {
  const bo = element.businessObject
  const moddle = bo.$model
  const existingExt = bo.extensionElements
  const existingValues: any[] = existingExt?.get('values') ?? []
  const filtered = existingValues.filter(
    (v: any) => !(v.$type === 'flowable:Field' && v.name === fieldName))
  if (value) {
    const isExpression = /^\$\{.+\}$/.test(value.trim())
    // @ts-ignore
    const field = isExpression
      ? moddle.create('flowable:Field', { name: fieldName, expression: value })
      : moddle.create('flowable:Field', { name: fieldName, string: value })
    filtered.push(field)
  }
  modeling.updateProperties(element, {
    extensionElements: moddle.create('bpmn:ExtensionElements', { values: filtered }),
  })
}
