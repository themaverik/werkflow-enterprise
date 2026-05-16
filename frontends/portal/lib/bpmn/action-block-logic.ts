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

/**
 * Sets the MANUAL_STEP `confirmationRequired` field, morphing between
 * `bpmn:ManualTask` and `bpmn:UserTask` when the toggle flips.
 *
 * On true + ManualTask  -> morph to UserTask, synthesize formKey="__werkflow_confirm_step__"
 * On false + UserTask   -> morph back to ManualTask, clear formKey
 * Otherwise             -> write directly on the current element (no morph)
 *
 * The morph is deferred via queueMicrotask so React (or Preact) state updates
 * settle before bpmn-js mutates the canvas. All post-morph writes operate on
 * the bpmnReplace return value, never on the closure-captured pre-morph element.
 *
 * Caller passes the bpmn-js Injector (or Modeler — both expose `.get(name)`).
 * Falls back to a direct write if the injector is unavailable or bpmnReplace throws.
 */
export function setManualStepConfirmation(
  element: any,
  injector: any,
  value: string,
): void {
  const modeling = injector?.get?.('modeling')
  if (!modeling) return

  if (!injector) {
    writeFlowableField(element, modeling, 'confirmationRequired', value)
    return
  }

  queueMicrotask(() => {
    try {
      const bpmnReplace = injector.get('bpmnReplace')
      if (value === 'true' && element.type === 'bpmn:ManualTask') {
        const morphed = bpmnReplace.replaceElement(element, { type: 'bpmn:UserTask' })
        modeling.updateProperties(morphed, {
          'flowable:actionType': 'MANUAL_STEP',
          formKey: '__werkflow_confirm_step__',
        })
        writeFlowableField(morphed, modeling, 'confirmationRequired', value)
        return
      }
      if (value === 'false' && element.type === 'bpmn:UserTask') {
        const morphed = bpmnReplace.replaceElement(element, { type: 'bpmn:ManualTask' })
        modeling.updateProperties(morphed, {
          'flowable:actionType': 'MANUAL_STEP',
          formKey: undefined,
        })
        writeFlowableField(morphed, modeling, 'confirmationRequired', value)
        return
      }
      writeFlowableField(element, modeling, 'confirmationRequired', value)
    } catch {
      writeFlowableField(element, modeling, 'confirmationRequired', value)
    }
  })
}
