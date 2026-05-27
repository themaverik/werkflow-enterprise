/**
 * Flowable Properties Provider
 *
 * Extends BPMN properties panel with Flowable-specific properties:
 * - Assignee (user assignment)
 * - Candidate Users (potential assignees)
 * - Candidate Groups (group assignments)
 * - Form Key (link to form, select dropdown populated from API)
 * - Priority
 * - Due Date Expression
 */

import { is } from 'bpmn-js/lib/util/ModelUtil'
import { html } from 'htm/preact'
import {
  SelectEntry,
  TextFieldEntry,
  isSelectEntryEdited,
  isTextFieldEntryEdited,
} from '@bpmn-io/properties-panel'
import { VariableComboBoxEntry } from '@/components/bpmn/VariableComboBoxEntry'
import type { CandidateGroupEntry } from '@/lib/platform/types'
import type { ProcessVariable } from '@/lib/api/dtds'
import { readFlowableField, writeFlowableField } from './extension-elements'


/**
 * Module-level variable for form schema options.
 * Set from BpmnDesigner.tsx after fetching from API.
 */
let formSchemaOptions: Array<{ key: string; name: string }> = []

export function setFormSchemaOptions(options: Array<{ key: string; name: string }>) {
  formSchemaOptions = options
}

export function getFormSchemaOptions(): Array<{ key: string; name: string }> {
  return formSchemaOptions
}

/**
 * Module-level variable for notification template options.
 * Set from BpmnDesigner.tsx after fetching from API.
 */
let notificationTemplateOptions: Array<{ key: string; name: string }> = []

export function setNotificationTemplateOptions(options: Array<{ key: string; name: string }>) {
  notificationTemplateOptions = options
}

export function getNotificationTemplates(): Array<{ key: string; name: string }> {
  return notificationTemplateOptions
}

/**
 * Module-level variable for group options.
 * Set from BpmnDesigner.tsx after fetching from API.
 */
let groupOptions: CandidateGroupEntry[] = []

export function setGroupOptions(options: CandidateGroupEntry[]) {
  groupOptions = options
}

/**
 * Module-level variable for process variables in scope at the selected UserTask.
 * Set from BpmnDesigner.tsx after fetching from DTDS variables-at endpoint.
 */
let processVariableOptions: ProcessVariable[] = []

export function setProcessVariableOptions(vars: ProcessVariable[]) {
  processVariableOptions = vars
}

/**
 * Module-level variable for custody variable group entries.
 * Set from BpmnDesigner.tsx after fetching from PSS feel-expressions endpoint.
 */
let custodyVarGroups: Array<{ key: string; label: string; pattern: string }> = []

export function setCustodyVarGroups(groups: Array<{ key: string; label: string; pattern: string }>) {
  custodyVarGroups = groups
}

/**
 * Module-level variable for registered JavaDelegate bean names.
 * Set from BpmnDesigner.tsx after fetching from GET /api/delegates.
 */
let delegateOptions: string[] = []

export function setDelegateOptions(options: string[]) {
  delegateOptions = options
}

/**
 * Module-level variable for the current user's roles.
 * Set from BpmnDesigner.tsx after session resolves.
 */
let currentUserRoles: string[] = []

export function setCurrentUserRoles(roles: string[]) {
  currentUserRoles = roles
}

/**
 * Module-level variable for process definition options (for Trigger Process dropdown).
 * Set from BpmnDesigner.tsx after fetching from API.
 */
let processDefinitionOptions: Array<{ key: string; name: string }> = []

export function setProcessDefinitionOptions(options: Array<{ key: string; name: string }>) {
  processDefinitionOptions = options
}

export function getProcessDefinitionOptions(): Array<{ key: string; name: string }> {
  return processDefinitionOptions
}

/**
 * Module-level variable for DMN decision options (for Business Rule Task decisionRef dropdown).
 * Set from BpmnDesigner.tsx after fetching from API.
 */
let dmnDecisionOptions: Array<{ key: string; name: string }> = []

export function setDmnDecisionOptions(options: Array<{ key: string; name: string }>) {
  dmnDecisionOptions = options
}

export function getDmnDecisionOptions(): Array<{ key: string; name: string }> {
  return dmnDecisionOptions
}

/**
 * Flowable properties provider for user tasks, start events, and service tasks.
 * Uses built-in SelectEntry / TextFieldEntry Preact components from @bpmn-io/properties-panel
 * so entries render correctly in the properties panel.
 */
class FlowablePropertiesProvider {
  static $inject = ['propertiesPanel', 'injector'];

  private _propertiesPanel: any;
  private _injector: any;

  constructor(propertiesPanel: any, injector: any) {
    this._propertiesPanel = propertiesPanel;
    this._injector = injector;

    propertiesPanel.registerProvider(500, this);
  }

  getGroups(element: any) {
    return (groups: any[]) => {
      const modeling = this._injector.get('modeling')
      const translate: (s: string) => string = this._injector.get('translate')
      const debounce = this._injector.get('debounceInput')
      const moddle = this._injector.get('moddle')
      const generalIdx = groups.findIndex((g: any) => g.id === 'general')

      // --- ReceiveTask deprecation notice ---
      if (is(element, 'bpmn:ReceiveTask')) {
        groups.splice(generalIdx + 1, 0, {
          id: 'deprecated',
          label: 'Deprecated',
          entries: [{
            id: 'receiveTask-deprecated',
            element,
            component: DeprecationNoticeEntry,
          }],
        })
      }

      // --- Action Block group — context-aware per element type ---
      // DMN service tasks (flowable:type="dmn") use the DMN Decision group below; skip action block.
      const applicable = isDmnServiceTask(element) ? [] : getApplicableActionTypes(element)
      if (applicable.length > 1) {
        const actionBlockEntries = buildActionBlockEntries(element, modeling, translate, debounce, this._injector)
        groups.splice(generalIdx + 1, 0, {
          id: 'action-block',
          label: 'Action Block',
          entries: actionBlockEntries,
        })
      }

      // --- User Task ---
      if (is(element, 'bpmn:UserTask')) {
        groups.splice(generalIdx + 1, 0, {
          id: 'flowable-task',
          label: 'Task Configuration',
          entries: [
            {
              id: 'priority',
              element,
              component: VariableComboBoxEntry,
              label: translate('Priority'),
              mode: 'single' as const,
              sourceKeys: ['dtds-variables-number'],
              processId: element.businessObject?.$parent?.id as string | undefined,
              activityId: element.businessObject?.id as string | undefined,
              getValue: () => element.businessObject.priority || '',
              setValue: (v: string) =>
                modeling.updateProperties(element, { priority: v || undefined }),
            },
            {
              id: 'dueDate',
              element,
              component: VariableComboBoxEntry,
              label: translate('Due Date'),
              mode: 'single' as const,
              sourceKeys: ['dtds-variables-date'],
              processId: element.businessObject?.$parent?.id as string | undefined,
              activityId: element.businessObject?.id as string | undefined,
              getValue: () => element.businessObject.dueDate || '',
              setValue: (v: string) =>
                modeling.updateProperties(element, { dueDate: v || undefined }),
            },
          ],
        })
      }

      // --- Start Event / Intermediate Events / Boundary Events ---
      if (isFormCapableElement(element)) {
        const formEntries: any[] = [formKeyEntry(element, modeling, translate)]
        if (isTriggerProcessCapable(element)) {
          formEntries.push(triggerProcessEntry(element, modeling, moddle, translate))
        }
        groups.splice(generalIdx + 1, 0, {
          id: 'flowable-forms',
          label: 'Forms',
          entries: formEntries,
        })
      }

      // --- DMN Decision Task (serviceTask flowable:type="dmn") ---
      // This is the ONLY working DMN authoring form in Flowable 7.2.
      // businessRuleTask + flowable:decisionRef is dead config (routes to legacy Drools engine).
      // See DmnDecisionTaskExecutionTest.java and ADR-026 for the verified contract.
      if (isDmnServiceTask(element) || (is(element, 'bpmn:ServiceTask') && !getActionType(element))) {
        groups.splice(generalIdx + 1, 0, {
          id: 'flowable-dmn',
          label: 'DMN Decision',
          entries: [
            {
              id: 'decisionTableReferenceKey',
              element,
              component: SelectEntry,
              isEdited: isSelectEntryEdited,
              label: translate('Decision Key'),
              description: translate(
                'Evaluates a DMN decision table. Outputs become process variables directly. ' +
                'Selecting a key marks this task as flowable:type="dmn".'
              ),
              getValue: () => readFlowableField(element, 'decisionTableReferenceKey'),
              setValue: (value: string) => {
                // Write the decisionTableReferenceKey as a <flowable:field> extension element
                writeFlowableField(element, modeling, 'decisionTableReferenceKey', value || '')
                // Set or clear flowable:type="dmn" on the serviceTask
                modeling.updateProperties(element, {
                  'flowable:type': value ? 'dmn' : undefined,
                })
                // Tint the shape so DMN tasks are visually distinct
                if (value) {
                  modeling.setColor(element, DMN_TASK_COLOURS)
                } else {
                  modeling.setColor(element, { fill: '#f9f9f9', stroke: '#bbb' })
                }
              },
              getOptions: () => {
                const options: Array<{ value: string; label: string }> = [
                  { value: '', label: translate('(none — plain service task)') },
                ]
                for (const d of dmnDecisionOptions) {
                  options.push({ value: d.key, label: d.name || d.key })
                }
                return options
              },
            },
            // Binding only matters once the task is actually a DMN task (a decision key is set);
            // omit it on a plain service task that's only a candidate for promotion.
            ...(isDmnServiceTask(element)
              ? [{
                  id: 'dmnDecisionBinding',
                  element,
                  component: SelectEntry,
                  isEdited: isSelectEntryEdited,
                  label: translate('Decision Binding'),
                  description: translate(
                    'Same deployment (default): evaluates the decision version bundled with this ' +
                    'process, so in-flight instances are reproducible. Latest: always evaluates the ' +
                    'newest deployed version of the decision.'
                  ),
                  // Flowable DmnActivityBehavior: sameDeployment field absent or "true" => same
                  // deployment; field "false" => latest. We clear the field for the default and
                  // write "false" only for the latest opt-in (writeFlowableField('') removes it).
                  // Any non-"false" stored value reads as same-deployment by design.
                  getValue: () =>
                    readFlowableField(element, 'sameDeployment') === 'false' ? 'latest' : 'same-deployment',
                  setValue: (value: string) => {
                    writeFlowableField(element, modeling, 'sameDeployment', value === 'latest' ? 'false' : '')
                  },
                  getOptions: () => [
                    { value: 'same-deployment', label: translate('Same deployment (pinned, default)') },
                    { value: 'latest', label: translate('Latest version') },
                  ],
                }]
              : []),
          ],
        })
      }

      // --- Script Task ---
      if (is(element, 'bpmn:ScriptTask')) {
        groups.splice(generalIdx + 1, 0, {
          id: 'flowable-script',
          label: 'Script',
          entries: [
            {
              id: 'scriptFormat',
              element,
              component: SelectEntry,
              isEdited: isSelectEntryEdited,
              label: translate('Script Format'),
              getValue: () => element.businessObject.scriptFormat || 'groovy',
              setValue: (value: string) =>
                modeling.updateProperties(element, { scriptFormat: value }),
              getOptions: () => [
                { value: 'groovy', label: 'Groovy' },
              ],
            },
            {
              id: 'script',
              element,
              component: TextFieldEntry,
              isEdited: isTextFieldEntryEdited,
              debounce,
              label: translate('Script'),
              description: translate("e.g. execution.setVariable('decision', 'approved')"),
              getValue: () => element.businessObject.script || '',
              setValue: (value: string) =>
                modeling.updateProperties(element, { script: value || undefined }),
            },
          ],
        })
      }

      // Service Configuration (delegate expression / Java class) is intentionally omitted.
      // All ServiceTask wiring is managed by the Action Block — users never set delegates directly.

      return groups
    }
  }
}

export default FlowablePropertiesProvider

// ---------------------------------------------------------------------------
// DeprecationNoticeEntry — read-only alert for deprecated BPMN elements.
// Rendered as a Preact component so it integrates with @bpmn-io/properties-panel
// without looking like an editable text field.
// ---------------------------------------------------------------------------

function DeprecationNoticeEntry({ id }: { id: string }) {
  return html`
    <div
      id=${id}
      role="alert"
      aria-live="polite"
      style="border-left: 3px solid #e65100; background: #fff8e1; padding: 8px; border-radius: 2px; font-size: 12px; line-height: 1.5;"
    >
      <strong>ReceiveTask is deprecated in Werkflow.</strong><br />
      Replace with a Message Intermediate Catch Event and a webhook connector.
    </div>
  `
}

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

/**
 * Returns true for BPMN elements that can have a form key attached.
 * UserTask and ServiceTask (HUMAN_APPROVAL) have their own separate wiring and are not included.
 */
function isFormCapableElement(element: any): boolean {
  return (
    is(element, 'bpmn:StartEvent') ||
    is(element, 'bpmn:IntermediateCatchEvent') ||
    is(element, 'bpmn:IntermediateThrowEvent') ||
    is(element, 'bpmn:BoundaryEvent')
  )
}

/**
 * Returns true for elements where a trigger process can be configured.
 * StartEvent is excluded — it fires before any execution variables exist.
 */
function isTriggerProcessCapable(element: any): boolean {
  return (
    is(element, 'bpmn:IntermediateCatchEvent') ||
    is(element, 'bpmn:IntermediateThrowEvent') ||
    is(element, 'bpmn:BoundaryEvent')
  )
}

const PROCESS_CALL_DELEGATE = '${processCallDelegate}'

function injectProcessCallListener(element: any, modeling: any, moddle: any) {
  let extensionElements = element.businessObject.extensionElements
  if (!extensionElements) {
    extensionElements = moddle.create('bpmn:ExtensionElements', { values: [] })
  }

  // Remove any existing processCallDelegate listener to avoid duplicates
  const filtered = (extensionElements.values || []).filter(
    (v: any) =>
      !(v.$type === 'flowable:ExecutionListener' && v.delegateExpression === PROCESS_CALL_DELEGATE)
  )

  // Create and add the listener
  const listener = moddle.create('flowable:ExecutionListener', {
    event: 'end',
    delegateExpression: PROCESS_CALL_DELEGATE,
  })
  filtered.push(listener)

  modeling.updateProperties(element, {
    extensionElements: moddle.create('bpmn:ExtensionElements', { values: filtered }),
  })
}

function removeProcessCallListener(element: any, modeling: any, moddle: any) {
  const existing = element.businessObject.extensionElements
  if (!existing?.values) return

  const filtered = existing.values.filter(
    (v: any) =>
      !(v.$type === 'flowable:ExecutionListener' && v.delegateExpression === PROCESS_CALL_DELEGATE)
  )

  if (filtered.length !== existing.values.length) {
    modeling.updateProperties(element, {
      extensionElements: moddle.create('bpmn:ExtensionElements', { values: filtered }),
    })
  }
}

function triggerProcessEntry(element: any, modeling: any, moddle: any, translate: (s: string) => string) {
  return {
    id: 'triggerProcess',
    element,
    component: SelectEntry,
    isEdited: isSelectEntryEdited,
    label: translate('Trigger Process'),
    description: translate('Start a linked process when this event fires'),
    getValue: () => element.businessObject.get('flowable:triggerProcess') || '',
    setValue: (value: string) => {
      modeling.updateProperties(element, {
        'flowable:triggerProcess': value || undefined,
      })
      if (value) {
        injectProcessCallListener(element, modeling, moddle)
      } else {
        removeProcessCallListener(element, modeling, moddle)
      }
    },
    getOptions: () => {
      const options: Array<{ value: string; label: string }> = [
        { value: '', label: translate('(none)') },
      ]
      for (const def of processDefinitionOptions) {
        options.push({ value: def.key, label: def.name || def.key })
      }
      return options
    },
  }
}

function formKeyEntry(element: any, modeling: any, translate: (s: string) => string) {
  return {
    id: 'formKey',
    element,
    component: SelectEntry,
    isEdited: isSelectEntryEdited,
    label: translate('Form Key'),
    description: translate('Select a form definition to link'),
    getValue: () =>
      element.businessObject.formKey ||
      element.businessObject.$attrs?.['flowable:formKey'] ||
      '',
    setValue: (value: string) =>
      modeling.updateProperties(element, { formKey: value || undefined }),
    getOptions: () => {
      const options: Array<{ value: string; label: string }> = [
        { value: '', label: translate('(none)') },
      ]
      for (const schema of formSchemaOptions) {
        options.push({ value: schema.key, label: schema.name || schema.key })
      }
      return options
    },
  }
}

// ---------------------------------------------------------------------------
// Action Block helpers
// ---------------------------------------------------------------------------

export const ACTION_TYPES = [
  { value: '', label: '(none)' },
  { value: 'HUMAN_APPROVAL',      label: 'Human Approval' },
  { value: 'SEND_NOTIFICATION',   label: 'Send Notification' },
  { value: 'CONNECTOR_OPERATION', label: 'Connector Operation' },
  { value: 'SET_VARIABLES',       label: 'Set Variables' },
  { value: 'CALL_SUBPROCESS',     label: 'Call Subprocess' },
  // GROOVY_SCRIPT action type quarantined pending ADR-016 — see docs/flowable-7.2/Script-Task.md D-SC-1 (Option C)
  { value: 'MANUAL_STEP',         label: 'Manual Step' },
]
// DMN route action type removed — zero in-flight usage confirmed 2026-05-16; native BusinessRuleTask replaces it

const ACTION_COLOURS: Record<string, { fill: string; stroke: string }> = {
  HUMAN_APPROVAL:      { fill: '#e3f2fd', stroke: '#1565c0' },
  SEND_NOTIFICATION:   { fill: '#fff3e0', stroke: '#e65100' },
  CONNECTOR_OPERATION: { fill: '#f3e5f5', stroke: '#6a1b9a' },
  SET_VARIABLES:       { fill: '#e0f2f1', stroke: '#00695c' },
  CALL_SUBPROCESS:     { fill: '#e8f5e9', stroke: '#2e7d32' },
  MANUAL_STEP:         { fill: '#f3e5f5', stroke: '#4a148c' },
}

const DELEGATE_MAP: Record<string, string> = {
  SEND_NOTIFICATION: '${notificationDelegate}',
  SET_VARIABLES:     '${setVariablesDelegate}',
}
// CALL_SUBPROCESS removed — native bpmn:CallActivity needs no delegate
// CONNECTOR_OPERATION has NO static delegate — transport is resolved when the connector
// is chosen in ConnectorOperationSection (rest → ${externalApiCallDelegate},
// database → ${databaseConnectorDelegate}). setActionType() clears the delegate
// expression so it is never left with a stale value.

const ACTION_TYPES_BY_ELEMENT: Record<string, string[]> = {
  'bpmn:UserTask':         ['', 'HUMAN_APPROVAL'],
  'bpmn:ServiceTask':      ['', 'CONNECTOR_OPERATION', 'SET_VARIABLES'],
  'bpmn:SendTask':         ['', 'SEND_NOTIFICATION'],
  'bpmn:ScriptTask':       [''], // GROOVY_SCRIPT quarantined — see ADR-016
  'bpmn:ManualTask':       ['', 'MANUAL_STEP'],
  'bpmn:CallActivity':     ['', 'CALL_SUBPROCESS'],
  // BusinessRuleTask: action block hidden — native DMN group is the UI
  'bpmn:Task':             ['', 'HUMAN_APPROVAL', 'SEND_NOTIFICATION', 'CONNECTOR_OPERATION',
                             'SET_VARIABLES', 'CALL_SUBPROCESS', 'MANUAL_STEP'], // GROOVY_SCRIPT quarantined — see ADR-016
  // ReceiveTask, SubProcess, all events: not in this map — action block hidden
}

export function getApplicableActionTypes(element: any): Array<{ value: string; label: string }> {
  const type = element?.businessObject?.$type ?? element?.type
  const allowed = ACTION_TYPES_BY_ELEMENT[type]
  if (!allowed) return []
  return ACTION_TYPES.filter(t => allowed.includes(t.value))
}

function getActionType(element: any): string {
  return element.businessObject.get('flowable:actionType') || ''
}

/** Target BPMN element type for each action type (ADR-009). */
const MORPH_TARGET: Record<string, string> = {
  HUMAN_APPROVAL:      'bpmn:UserTask',
  SEND_NOTIFICATION:   'bpmn:SendTask',
  CONNECTOR_OPERATION: 'bpmn:ServiceTask',
  SET_VARIABLES:       'bpmn:ServiceTask',
  CALL_SUBPROCESS:     'bpmn:CallActivity',
  MANUAL_STEP:         'bpmn:ManualTask',
}

export function setActionType(element: any, modeling: any, value: string, injector?: any) {
  let target = element
  const targetType = value ? MORPH_TARGET[value] : undefined

  // Morph the element to the correct BPMN type when a target type is defined and element differs
  if (targetType && element.type !== targetType && injector) {
    try {
      const bpmnReplace = injector.get('bpmnReplace')
      target = bpmnReplace.replaceElement(element, { type: targetType })
    } catch {
      // replaceElement unavailable (e.g. read-only viewer) — proceed as-is
    }
  }

  modeling.updateProperties(target, { 'flowable:actionType': value || undefined })

  // Set or clear delegateExpression based on DELEGATE_MAP.
  // CONNECTOR_OPERATION has no static delegate — it is resolved by ConnectorOperationSection
  // when the user picks a connector. Clear it here so no stale delegate survives the switch.
  const delegate = DELEGATE_MAP[value] ?? undefined
  modeling.updateProperties(target, {
    'flowable:delegateExpression': delegate,
    delegateExpression: delegate,
  })

  // GROOVY_SCRIPT scriptFormat seed removed — action type quarantined (ADR-016)

  // SEND_NOTIFICATION: seed channel default so delegate never throws "Required field not set"
  if (value === 'SEND_NOTIFICATION') {
    if (!readFlowableField(target, 'channel')) {
      writeFlowableField(target, modeling, 'channel', 'email')
    }
  }

  // CALL_SUBPROCESS: clear any legacy delegate expression — native CallActivity uses no delegate
  if (value === 'CALL_SUBPROCESS') {
    modeling.updateProperties(target, {
      'flowable:delegateExpression': undefined,
      delegateExpression: undefined,
    })
  }

  if (value && ACTION_COLOURS[value]) {
    modeling.setColor(target, ACTION_COLOURS[value])
  } else {
    modeling.setColor(target, { fill: '#f9f9f9', stroke: '#bbb' })
  }
}

function buildActionBlockEntries(
  element: any,
  modeling: any,
  translate: (s: string) => string,
  debounce: any,
  injector?: any
): any[] {
  const entries: any[] = [
    {
      id: 'actionType',
      element,
      component: SelectEntry,
      isEdited: isSelectEntryEdited,
      label: translate('Action Type'),
      getValue: () => getActionType(element),
      setValue: (value: string) => setActionType(element, modeling, value, injector),
      getOptions: () => getApplicableActionTypes(element).map(t => ({ value: t.value, label: translate(t.label) })),
    },
  ]

  const actionType = getActionType(element)

  // HUMAN_APPROVAL and SEND_NOTIFICATION are rendered in ServiceTaskPropertiesPanel
  // (React sidebar) for consistent card-based styling — no entries pushed here.

  // CONNECTOR_OPERATION fields are rendered exclusively in ConnectorOperationSection
  // (React sidebar) — no native panel entries needed.

  if (actionType === 'CALL_SUBPROCESS') {
    entries.push(
      // 1) calledElement — native BPMN 2.0 attribute on CallActivity; do NOT use flowable:field
      {
        id: 'sub-calledElement',
        element,
        component: SelectEntry,
        isEdited: isSelectEntryEdited,
        label: translate('Process Key (calledElement)'),
        description: translate('Subprocess definition to invoke'),
        getValue: () => element.businessObject.get('calledElement') || '',
        setValue: (value: string) =>
          modeling.updateProperties(element, { calledElement: value || undefined }),
        getOptions: () => {
          if (processDefinitionOptions.length === 0) {
            return [
              { value: '', label: translate('(no deployed processes — deploy first)') },
            ]
          }
          const options: Array<{ value: string; label: string }> = [
            { value: '', label: translate('(select process)') },
          ]
          for (const def of processDefinitionOptions) {
            options.push({ value: def.key, label: def.name || def.key })
          }
          return options
        },
      },
    )
    // 2) In/Out variable mapping entries (serialize as <flowable:in> / <flowable:out>)
    entries.push(...buildVarMappingEntries(element, modeling, translate, debounce, 'flowable:In', 'In Mappings (parent → child)'))
    entries.push(...buildVarMappingEntries(element, modeling, translate, debounce, 'flowable:Out', 'Out Mappings (child → parent)'))
  }

  // GROOVY_SCRIPT action block entries removed — action type quarantined (ADR-016)

  if (actionType === 'MANUAL_STEP') {
    entries.push(
      {
        id: 'ms-description',
        element,
        component: TextFieldEntry,
        isEdited: isTextFieldEntryEdited,
        debounce,
        label: translate('Step Description'),
        description: translate('Instructions shown to the assignee.'),
        getValue: () => readFlowableField(element, 'stepDescription') || '',
        setValue: (value: string) => writeFlowableField(element, modeling, 'stepDescription', value || ''),
      },
    )
  }

  return entries
}

function textField(
  element: any, modeling: any, translate: (s: string) => string,
  debounce: any, id: string, label: string, prop: string, placeholder: string | null
): any {
  return {
    id,
    element,
    component: TextFieldEntry,
    isEdited: isTextFieldEntryEdited,
    debounce,
    label,
    description: placeholder ? translate(placeholder) : undefined,
    getValue: () => element.businessObject.get(prop) || '',
    setValue: (value: string) =>
      modeling.updateProperties(element, { [prop]: value || undefined }),
  }
}

/** Colour scheme for DMN service tasks — visually distinct from action-block tasks. */
export const DMN_TASK_COLOURS = { fill: '#e8eaf6', stroke: '#283593' }

/**
 * Returns true when element is a bpmn:ServiceTask with flowable:type="dmn".
 * This is the Flowable 7.2 native DMN authoring form (verified by DmnDecisionTaskExecutionTest).
 */
export function isDmnServiceTask(element: any): boolean {
  if (!is(element, 'bpmn:ServiceTask')) return false
  return element.businessObject?.get('flowable:type') === 'dmn'
}

// readFlowableField / writeFlowableField now live in ./extension-elements.
// Consumers should import from there directly; provider no longer re-exports them.

// Writes value as a <flowable:field> extension element (required for fields read by JavaDelegate)
function flowableFieldEntry(
  element: any, modeling: any, translate: (s: string) => string,
  debounce: any, id: string, label: string, fieldName: string
): any {
  return {
    id,
    element,
    component: TextFieldEntry,
    isEdited: isTextFieldEntryEdited,
    debounce,
    label,
    getValue: () => readFlowableField(element, fieldName),
    setValue: (value: string) => writeFlowableField(element, modeling, fieldName, value),
  }
}

/**
 * Builds In or Out variable mapping entries for CALL_SUBPROCESS.
 * Each mapping serializes as <flowable:in source="x" target="y"/> or <flowable:out ...>.
 *
 * Simple shape chosen (text fields per row) over a full multi-row table component to
 * stay within the 50-line guideline and match the properties-panel TextFieldEntry API.
 * The invariant that matters is BPMN XML serializes as <flowable:in/out> elements,
 * NOT as <flowable:field> — this is correct because moddle creates 'flowable:In'/'flowable:Out'.
 */
function buildVarMappingEntries(
  element: any, modeling: any, translate: (s: string) => string,
  debounce: any, moddleType: 'flowable:In' | 'flowable:Out', groupLabel: string
): any[] {
  const bo = element.businessObject
  const moddle = bo.$model

  const getMappings = (): Array<{ source: string; target: string }> => {
    const ext = bo.extensionElements
    if (!ext?.values) return []
    return (ext.values as any[])
      .filter((v: any) => v.$type === moddleType)
      .map((v: any) => ({ source: v.source ?? v.sourceExpression ?? '', target: v.target ?? '' }))
  }

  const writeMappings = (mappings: Array<{ source: string; target: string }>) => {
    const existingExt = bo.extensionElements
    const others = (existingExt?.values ?? []).filter((v: any) => v.$type !== moddleType)
    const newItems = mappings
      .filter(m => m.source.trim() || m.target.trim())
      .map(m => {
        const isExpr = /^\$\{.+\}$/.test(m.source.trim())
        return isExpr
          ? moddle.create(moddleType, { sourceExpression: m.source, target: m.target })
          : moddle.create(moddleType, { source: m.source, target: m.target })
      })
    modeling.updateProperties(element, {
      extensionElements: moddle.create('bpmn:ExtensionElements', { values: [...others, ...newItems] }),
    })
  }

  const currentMappings = getMappings()
  const entries: any[] = []

  // Label header as a read-only text entry
  entries.push({
    id: `${moddleType}-label`,
    element,
    component: TextFieldEntry,
    isEdited: () => false,
    label: translate(groupLabel),
    getValue: () => '',
    setValue: () => {},
  })

  // One row per existing mapping
  currentMappings.forEach((mapping, idx) => {
    entries.push({
      id: `${moddleType}-source-${idx}`,
      element,
      component: TextFieldEntry,
      isEdited: isTextFieldEntryEdited,
      debounce,
      label: translate('Source'),
      getValue: () => mapping.source,
      setValue: (value: string) => {
        const updated = getMappings()
        if (updated[idx]) { updated[idx] = { ...updated[idx], source: value } }
        writeMappings(updated)
      },
    })
    entries.push({
      id: `${moddleType}-target-${idx}`,
      element,
      component: TextFieldEntry,
      isEdited: isTextFieldEntryEdited,
      debounce,
      label: translate('Target'),
      getValue: () => mapping.target,
      setValue: (value: string) => {
        const updated = getMappings()
        if (updated[idx]) { updated[idx] = { ...updated[idx], target: value } }
        writeMappings(updated)
      },
    })
  })

  return entries
}

function templateKeySelectEntry(element: any, modeling: any, translate: (s: string) => string): any {
  const currentKey = readFlowableField(element, 'templateKey') || ''
  const editHref = currentKey
    ? `/admin/email-templates/${encodeURIComponent(currentKey)}`
    : '/admin/email-templates/new'
  const editLabel = currentKey ? translate('Edit template →') : translate('Create template →')

  return {
    id: 'ab-templateKey',
    element,
    component: SelectEntry,
    isEdited: isSelectEntryEdited,
    label: translate('Template Key'),
    description: editLabel + ` (${editHref})`,
    getValue: () => currentKey,
    setValue: (value: string) => writeFlowableField(element, modeling, 'templateKey', value || ''),
    getOptions: () => {
      const options: Array<{ value: string; label: string }> = [
        { value: '', label: translate('(select template)') },
      ]
      for (const t of notificationTemplateOptions) {
        options.push({ value: t.key, label: t.name || t.key })
      }
      return options
    },
  }
}

function channelSelectEntry(element: any, modeling: any, translate: (s: string) => string): any {
  return {
    id: 'ab-channel',
    element,
    component: SelectEntry,
    isEdited: isSelectEntryEdited,
    label: translate('Channel'),
    getValue: () => readFlowableField(element, 'channel') || 'email',
    setValue: (value: string) => writeFlowableField(element, modeling, 'channel', value || 'email'),
    getOptions: () => [
      { value: 'email',    label: translate('Email') },
      // Coming-soon channels are included for discoverability but marked disabled
      // so they cannot be selected and written to the BPMN.
      { value: 'slack',    label: translate('Slack (coming soon)'),    disabled: true },
      { value: 'whatsapp', label: translate('WhatsApp (coming soon)'), disabled: true },
    ],
  }
}

