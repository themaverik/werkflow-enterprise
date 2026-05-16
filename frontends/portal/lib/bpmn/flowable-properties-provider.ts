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
 * Module-level variable for webhook connector options (for Message Event connector picker).
 * Set from BpmnDesigner.tsx after fetching from DTDS connector list.
 */
let connectorOptions: Array<{ key: string; name: string }> = []

export function setConnectorOptions(options: Array<{ key: string; name: string }>) {
  connectorOptions = options
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
      const applicable = getApplicableActionTypes(element)
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
          id: 'flowable-forms',
          label: 'Forms',
          entries: [formKeyEntry(element, modeling, translate)],
        })

        groups.splice(generalIdx + 2, 0, {
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

      // --- Signal Events (Flowable-specific: signal name picker + correlation key) ---
      if (hasSignalDefinition(element)) {
        // Collect signal names defined in the diagram for the enum picker
        const definitions = element.businessObject.$parent?.$parent ?? element.businessObject.$parent
        const signalOptions: Array<{ value: string; label: string }> = [
          { value: '', label: translate('(select signal)') },
        ]
        try {
          const rootElements = definitions?.rootElements ?? []
          for (const el of rootElements) {
            if (el.$type === 'bpmn:Signal' && el.name) {
              signalOptions.push({ value: el.name, label: el.name })
            }
          }
        } catch {
          // diagram walk failed — options remain empty (free entry still works via correlationKey)
        }

        groups.splice(generalIdx + 1, 0, {
          id: 'flowable-signal',
          label: 'Signal (Flowable)',
          entries: [
            {
              id: 'signalName',
              element,
              component: SelectEntry,
              isEdited: isSelectEntryEdited,
              label: translate('Signal Name'),
              description: translate('Select a signal defined in this diagram.'),
              getValue: () => element.businessObject.get('flowable:signalName') || '',
              setValue: (value: string) =>
                modeling.updateProperties(element, {
                  'flowable:signalName': value || undefined,
                }),
              getOptions: () => signalOptions,
            },
            {
              id: 'signalCorrelationKey',
              element,
              component: TextFieldEntry,
              isEdited: isTextFieldEntryEdited,
              debounce,
              label: translate('Correlation Key'),
              description: translate(
                'Optional — target a specific process instance. Leave blank for broadcast (global scope).'
              ),
              getValue: () =>
                element.businessObject.get('flowable:correlationKey') || '',
              setValue: (value: string) =>
                modeling.updateProperties(element, {
                  'flowable:correlationKey': value || undefined,
                }),
            },
          ],
        })
      }

      // --- Message Events (Webhook correlation config) ---
      if (hasMessageDefinition(element)) {
        groups.splice(generalIdx + 1, 0, {
          id: 'flowable-message',
          label: 'Message (Webhook)',
          entries: [
            {
              id: 'webhookConnector',
              element,
              component: SelectEntry,
              isEdited: isSelectEntryEdited,
              label: translate('Connector'),
              description: translate('Webhook connector that publishes this message'),
              getValue: () => element.businessObject.get('flowable:webhookConnector') || '',
              setValue: (value: string) =>
                modeling.updateProperties(element, {
                  'flowable:webhookConnector': value || undefined,
                }),
              getOptions: () => {
                const options: Array<{ value: string; label: string }> = [
                  { value: '', label: translate('(none)') },
                ]
                for (const c of connectorOptions) {
                  options.push({ value: c.key, label: c.name || c.key })
                }
                return options
              },
            },
            {
              id: 'correlationExpression',
              element,
              component: TextFieldEntry,
              isEdited: isTextFieldEntryEdited,
              debounce,
              label: translate('Correlation Expression'),
              description: translate(
                'FEEL expression evaluated against payload — value used to match the running process instance'
              ),
              getValue: () =>
                element.businessObject.get('flowable:correlationExpression') || '',
              setValue: (value: string) =>
                modeling.updateProperties(element, {
                  'flowable:correlationExpression': value || undefined,
                }),
            },
          ],
        })
      }

      // --- Business Rule Task (DMN) ---
      if (is(element, 'bpmn:BusinessRuleTask')) {
        groups.splice(generalIdx + 1, 0, {
          id: 'flowable-dmn',
          label: 'DMN Decision',
          entries: [
            {
              id: 'decisionRef',
              element,
              component: SelectEntry,
              isEdited: isSelectEntryEdited,
              label: translate('Decision Reference'),
              description: translate('DMN decision key to evaluate (e.g. doa_routing)'),
              getValue: () =>
                element.businessObject.get('flowable:decisionRef') || '',
              setValue: (value: string) =>
                modeling.updateProperties(element, {
                  'flowable:decisionRef': value || undefined,
                }),
              getOptions: () => {
                const options: Array<{ value: string; label: string }> = [
                  { value: '', label: translate('(select decision)') },
                ]
                for (const d of dmnDecisionOptions) {
                  options.push({ value: d.key, label: d.name || d.key })
                }
                return options
              },
            },
            {
              id: 'mapDecisionResult',
              element,
              component: SelectEntry,
              isEdited: isSelectEntryEdited,
              label: translate('Map Decision Result'),
              getValue: () =>
                element.businessObject.get('flowable:mapDecisionResult') || 'singleEntry',
              setValue: (value: string) =>
                modeling.updateProperties(element, {
                  'flowable:mapDecisionResult': value,
                }),
              getOptions: () => [
                { value: 'singleEntry', label: translate('Single Entry (first matched rule, first output)') },
                { value: 'singleResult', label: translate('Single Result (first matched rule, all outputs)') },
                { value: 'resultList', label: translate('Result List (all matched rules)') },
                { value: 'collectEntries', label: translate('Collect Entries (all values for one output)') },
              ],
            },
            {
              id: 'resultVariable',
              element,
              component: TextFieldEntry,
              isEdited: isTextFieldEntryEdited,
              debounce,
              label: translate('Result Variable'),
              description: translate('Process variable name to store the decision output (e.g. approverGroup)'),
              getValue: () =>
                element.businessObject.get('flowable:resultVariable') || '',
              setValue: (value: string) =>
                modeling.updateProperties(element, {
                  'flowable:resultVariable': value || undefined,
                }),
            },
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
              getValue: () => element.businessObject.scriptFormat || 'javascript',
              setValue: (value: string) =>
                modeling.updateProperties(element, { scriptFormat: value }),
              getOptions: () => [
                { value: 'javascript', label: 'JavaScript' },
                { value: 'groovy',     label: 'Groovy' },
                { value: 'juel',       label: 'JUEL' },
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
 * Returns true when the element has a signal event definition.
 * Covers throw (IntermediateThrowEvent, EndEvent) and catch (IntermediateCatchEvent, BoundaryEvent).
 */
function hasSignalDefinition(element: any): boolean {
  const defs: any[] = element.businessObject.eventDefinitions || []
  return defs.some((d: any) => d.$type === 'bpmn:SignalEventDefinition')
}

function hasMessageDefinition(element: any): boolean {
  const defs: any[] = element.businessObject.eventDefinitions || []
  return defs.some((d: any) => d.$type === 'bpmn:MessageEventDefinition')
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
  { value: 'HUMAN_APPROVAL',    label: 'Human Approval' },
  { value: 'SEND_NOTIFICATION', label: 'Send Notification' },
  { value: 'EXTERNAL_API_CALL', label: 'External API Call' },
  { value: 'SET_VARIABLES',     label: 'Set Variables' },
  { value: 'CALL_SUBPROCESS',   label: 'Call Subprocess' },
  { value: 'GROOVY_SCRIPT',     label: 'Groovy Script (Admin)' },
  { value: 'MANUAL_STEP',       label: 'Manual Step' },
]
// DMN route action type removed — zero in-flight usage confirmed 2026-05-16; native BusinessRuleTask replaces it

const ACTION_COLOURS: Record<string, { fill: string; stroke: string }> = {
  HUMAN_APPROVAL:    { fill: '#e3f2fd', stroke: '#1565c0' },
  SEND_NOTIFICATION: { fill: '#fff3e0', stroke: '#e65100' },
  EXTERNAL_API_CALL: { fill: '#f3e5f5', stroke: '#6a1b9a' },
  SET_VARIABLES:     { fill: '#e0f2f1', stroke: '#00695c' },
  CALL_SUBPROCESS:   { fill: '#e8f5e9', stroke: '#2e7d32' },
  GROOVY_SCRIPT:     { fill: '#fce4ec', stroke: '#c62828' },
  MANUAL_STEP:       { fill: '#f3e5f5', stroke: '#4a148c' },
}

const DELEGATE_MAP: Record<string, string> = {
  SEND_NOTIFICATION: '${notificationDelegate}',
  EXTERNAL_API_CALL: '${externalApiCallDelegate}',
  SET_VARIABLES:     '${setVariablesDelegate}',
}
// CALL_SUBPROCESS removed — native bpmn:CallActivity needs no delegate

const ACTION_TYPES_BY_ELEMENT: Record<string, string[]> = {
  'bpmn:UserTask':         ['', 'HUMAN_APPROVAL'],
  'bpmn:ServiceTask':      ['', 'EXTERNAL_API_CALL', 'SET_VARIABLES'],
  'bpmn:SendTask':         ['', 'SEND_NOTIFICATION'],
  'bpmn:ScriptTask':       ['', 'GROOVY_SCRIPT'],
  'bpmn:ManualTask':       ['', 'MANUAL_STEP'],
  'bpmn:CallActivity':     ['', 'CALL_SUBPROCESS'],
  // BusinessRuleTask: action block hidden — native DMN group is the UI
  'bpmn:Task':             ['', 'HUMAN_APPROVAL', 'SEND_NOTIFICATION', 'EXTERNAL_API_CALL',
                             'SET_VARIABLES', 'CALL_SUBPROCESS', 'GROOVY_SCRIPT', 'MANUAL_STEP'],
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
  HUMAN_APPROVAL:    'bpmn:UserTask',
  SEND_NOTIFICATION: 'bpmn:SendTask',
  EXTERNAL_API_CALL: 'bpmn:ServiceTask',
  SET_VARIABLES:     'bpmn:ServiceTask',
  CALL_SUBPROCESS:   'bpmn:CallActivity',
  GROOVY_SCRIPT:     'bpmn:ScriptTask',
  MANUAL_STEP:       'bpmn:ManualTask',
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

  // Set or clear delegateExpression based on DELEGATE_MAP
  const delegate = DELEGATE_MAP[value] ?? undefined
  modeling.updateProperties(target, {
    'flowable:delegateExpression': delegate,
    delegateExpression: delegate,
  })

  // GROOVY_SCRIPT: ScriptTask requires a scriptFormat attribute
  if (value === 'GROOVY_SCRIPT') {
    modeling.updateProperties(target, { scriptFormat: 'groovy' })
  }

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

  if (actionType === 'EXTERNAL_API_CALL') {
    entries.push(
      textField(element, modeling, translate, debounce, 'ab-url',
        translate('URL'), 'ab:url', 'https://api.example.com/...'),
      methodSelectEntry(element, modeling, translate),
      textField(element, modeling, translate, debounce, 'ab-secretRef',
        translate('Secret Reference'), 'ab:secretRef', null),
      textField(element, modeling, translate, debounce, 'ab-responseVariable',
        translate('Store Response As'), 'ab:responseVariable', 'apiResult'),
      textField(element, modeling, translate, debounce, 'ab-extractFields',
        translate('Extract Fields'), 'ab:extractFields', 'varName:$.path.to.field'),
      textField(element, modeling, translate, debounce, 'ab-maskFields',
        translate('Mask Fields'), 'ab:maskFields', '$.auth,$.token'),
      onErrorSelectEntry(element, modeling, translate),
      textField(element, modeling, translate, debounce, 'ab-sampleResponseJson',
        translate('Sample Response JSON'), 'ab:sampleResponseJson',
        translate('{"id": 123, "status": "approved"}')),
    )
  }

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

  if (actionType === 'GROOVY_SCRIPT') {
    entries.push(
      {
        id: 'gs-script',
        element,
        component: TextFieldEntry,
        isEdited: isTextFieldEntryEdited,
        debounce,
        label: translate('Groovy Script'),
        description: translate('Admin-restricted. execution.setVariable("key", value) to write process variables.'),
        getValue: () => element.businessObject.get('flowable:script') || '',
        setValue: (value: string) =>
          modeling.updateProperties(element, { 'flowable:script': value || undefined }),
      },
    )
  }

  if (actionType === 'SET_VARIABLES') {
    entries.push(...buildSetVariablesEntries(element, modeling, translate, debounce))
  }

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
      {
        id: 'ms-confirmationRequired',
        element,
        component: SelectEntry,
        isEdited: isSelectEntryEdited,
        label: translate('Confirmation Required'),
        getValue: () => readFlowableField(element, 'confirmationRequired') || 'false',
        setValue: (value: string) => {
          if (injector) {
            queueMicrotask(() => {
              try {
                const bpmnReplace = injector.get('bpmnReplace')
                if (value === 'true' && element.type === 'bpmn:ManualTask') {
                  // Morph ManualTask → UserTask; write confirmationRequired on morphed element
                  const morphed = bpmnReplace.replaceElement(element, { type: 'bpmn:UserTask' })
                  modeling.updateProperties(morphed, {
                    'flowable:actionType': 'MANUAL_STEP',
                    formKey: '__werkflow_confirm_step__',
                  })
                  writeFlowableField(morphed, modeling, 'confirmationRequired', value)
                } else if (value === 'false' && element.type === 'bpmn:UserTask') {
                  // Morph UserTask → ManualTask; write confirmationRequired on morphed element
                  const morphed = bpmnReplace.replaceElement(element, { type: 'bpmn:ManualTask' })
                  modeling.updateProperties(morphed, {
                    'flowable:actionType': 'MANUAL_STEP',
                    formKey: undefined,
                  })
                  writeFlowableField(morphed, modeling, 'confirmationRequired', value)
                } else {
                  // No morph needed — write directly on current element
                  writeFlowableField(element, modeling, 'confirmationRequired', value)
                }
              } catch {
                // bpmnReplace unavailable — write on current element as fallback
                writeFlowableField(element, modeling, 'confirmationRequired', value)
              }
            })
          } else {
            writeFlowableField(element, modeling, 'confirmationRequired', value)
          }
        },
        getOptions: () => [
          { value: 'false', label: translate('No') },
          { value: 'true',  label: translate('Yes') },
        ],
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

// Read/write <flowable:field> by name — shared by flowableFieldEntry and SelectEntry variants
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

/**
 * Builds SET_VARIABLES panel entries.
 * Each variable assignment serializes as <flowable:field name="var.<name>">.
 * Variable name and value are editable; value may be a literal or ${expression}.
 */
function buildSetVariablesEntries(
  element: any, modeling: any, translate: (s: string) => string, debounce: any
): any[] {
  const getVarFields = (): Array<{ name: string; value: string }> => {
    const ext = element.businessObject.extensionElements
    if (!ext?.values) return []
    return (ext.values as any[])
      .filter((v: any) => v.$type === 'flowable:Field' && (v.name ?? '').startsWith('var.'))
      .map((v: any) => ({ name: v.name.slice(4), value: v.expression ?? v.string ?? '' }))
  }

  const entries: any[] = []
  const vars = getVarFields()

  vars.forEach((v, idx) => {
    entries.push({
      id: `sv-name-${idx}`,
      element,
      component: TextFieldEntry,
      isEdited: isTextFieldEntryEdited,
      debounce,
      label: translate('Variable Name'),
      getValue: () => v.name,
      setValue: (value: string) => {
        // Rename: remove old field, write new field with same value
        const current = getVarFields()
        if (current[idx]) {
          writeFlowableField(element, modeling, `var.${current[idx].name}`, '')
          if (value.trim()) {
            writeFlowableField(element, modeling, `var.${value.trim()}`, current[idx].value)
          }
        }
      },
    })
    entries.push({
      id: `sv-value-${idx}`,
      element,
      component: TextFieldEntry,
      isEdited: isTextFieldEntryEdited,
      debounce,
      label: translate('Value (literal or ${expression})'),
      getValue: () => v.value,
      setValue: (value: string) => {
        const current = getVarFields()
        if (current[idx]) {
          writeFlowableField(element, modeling, `var.${current[idx].name}`, value)
        }
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

function methodSelectEntry(element: any, modeling: any, translate: (s: string) => string): any {
  return {
    id: 'ab-method',
    element,
    component: SelectEntry,
    isEdited: isSelectEntryEdited,
    label: translate('Method'),
    getValue: () => element.businessObject.get('ab:method') || 'GET',
    setValue: (value: string) =>
      modeling.updateProperties(element, { 'ab:method': value }),
    getOptions: () =>
      ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'].map(m => ({ value: m, label: m })),
  }
}

function onErrorSelectEntry(element: any, modeling: any, translate: (s: string) => string): any {
  return {
    id: 'ab-onError',
    element,
    component: SelectEntry,
    isEdited: isSelectEntryEdited,
    label: translate('On Error'),
    getValue: () => element.businessObject.get('ab:onError') || 'FAIL',
    setValue: (value: string) =>
      modeling.updateProperties(element, { 'ab:onError': value }),
    getOptions: () => [
      { value: 'FAIL',             label: translate('Fail') },
      { value: 'CONTINUE',         label: translate('Continue') },
      { value: 'THROW_BPMN_ERROR', label: translate('Throw BPMN Error') },
    ],
  }
}
