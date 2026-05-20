/**
 * Shared action-block orchestration used by both the bpmn-js properties panel
 * (flowable-properties-provider.ts) and the React sidebar action sections
 * (components/bpmn/sections/*).
 *
 * These functions are the single source of truth for BPMN-XML-producing
 * operations that previously diverged between the two UIs. Keeping them here
 * guarantees both UIs emit identical BPMN.
 */

import { readFlowableField, writeFlowableField } from './extension-elements'

export interface VarFieldRow {
  name: string
  value: string
}

/**
 * Reads SET_VARIABLES var.<name> fields from an element's extensionElements.
 * Returns one row per <flowable:field> whose name has the "var." prefix.
 * The row's `value` is the field's expression body if present, else its string body.
 */
export function readVarFields(element: any): VarFieldRow[] {
  const ext = element.businessObject?.extensionElements
  if (!ext?.values) return []
  return (ext.values as any[])
    .filter((v: any) => v.$type === 'flowable:Field' && (v.name ?? '').startsWith('var.'))
    .map((v: any) => ({ name: v.name.slice(4), value: v.expression ?? v.string ?? '' }))
}

