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

      // --- Action Block group (any Task, including UserTask and ServiceTask) ---
      const isTask = is(element, 'bpmn:Task')
      if (isTask) {
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

      // --- Service Task ---
      // Only show low-level Service Configuration (delegate/class) when no Action Type is set.
      // When an Action Type is configured, setActionType auto-manages the delegate expression.
      if (is(element, 'bpmn:ServiceTask') && !element.businessObject.get('flowable:actionType')) {
        groups.splice(generalIdx + 1, 0, {
          id: 'flowable-service-task',
          label: 'Service Configuration',
          entries: [
            {
              id: 'delegateExpression',
              element,
              component: SelectEntry,
              isEdited: isSelectEntryEdited,
              label: translate('Delegate Expression'),
              getValue: () =>
                element.businessObject.get('flowable:delegateExpression') ||
                element.businessObject.delegateExpression ||
                '',
              setValue: (value: string) =>
                modeling.updateProperties(element, {
                  'flowable:delegateExpression': value || undefined,
                  delegateExpression: value || undefined,
                }),
              getOptions: () => [
                { value: '', label: translate('(none)') },
                ...delegateOptions.map(name => ({
                  value: `\${${name}}`,
                  label: name,
                })),
              ],
            },
            {
              id: 'class',
              element,
              component: TextFieldEntry,
              isEdited: isTextFieldEntryEdited,
              debounce,
              label: translate('Java Class'),
              description: translate('Fully qualified Java class name'),
              getValue: () =>
                element.businessObject.get('flowable:class') ||
                element.businessObject.class ||
                '',
              setValue: (value: string) =>
                modeling.updateProperties(element, {
                  'flowable:class': value || undefined,
                  class: value || undefined,
                }),
            },
          ],
        })
      }

      return groups
    }
  }
}

export default FlowablePropertiesProvider

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

const ACTION_TYPES = [
  { value: '', label: '(none)' },
  { value: 'HUMAN_APPROVAL',    label: 'Human Approval' },
  { value: 'SEND_NOTIFICATION', label: 'Send Notification' },
  { value: 'EXTERNAL_API_CALL', label: 'External API Call' },
  { value: 'CALL_SUBPROCESS',   label: 'Call Subprocess' },
  { value: 'DMN_ROUTE',         label: 'DMN Route' },
  { value: 'GROOVY_SCRIPT',     label: 'Groovy Script (Admin)' },
  { value: 'MANUAL_STEP',       label: 'Manual Step' },
]

const ACTION_COLOURS: Record<string, { fill: string; stroke: string }> = {
  HUMAN_APPROVAL:    { fill: '#e3f2fd', stroke: '#1565c0' },
  SEND_NOTIFICATION: { fill: '#fff3e0', stroke: '#e65100' },
  EXTERNAL_API_CALL: { fill: '#f3e5f5', stroke: '#6a1b9a' },
  CALL_SUBPROCESS:   { fill: '#e8f5e9', stroke: '#2e7d32' },
  DMN_ROUTE:         { fill: '#e8eaf6', stroke: '#283593' },
  GROOVY_SCRIPT:     { fill: '#fce4ec', stroke: '#c62828' },
  MANUAL_STEP:       { fill: '#f3e5f5', stroke: '#4a148c' },
}

const DELEGATE_MAP: Record<string, string> = {
  SEND_NOTIFICATION: '${emailActionDelegate}',
  EXTERNAL_API_CALL: '${externalApiCallDelegate}',
  CALL_SUBPROCESS:   '${callSubprocessDelegate}',
}

function getActionType(element: any): string {
  return element.businessObject.get('flowable:actionType') || ''
}

export function setActionType(element: any, modeling: any, value: string, injector?: any) {
  // Ensure the element is a ServiceTask — delegateExpression is invalid on plain <task>.
  // If the user placed a generic Task and then set an action type, morph it to ServiceTask first.
  let target = element
  if (value && element.type !== 'bpmn:ServiceTask' && injector) {
    try {
      const bpmnReplace = injector.get('bpmnReplace')
      target = bpmnReplace.replaceElement(element, { type: 'bpmn:ServiceTask' })
    } catch {
      // replaceElement unavailable (e.g. read-only viewer) — proceed as-is
    }
  }

  modeling.updateProperties(target, { 'flowable:actionType': value || undefined })

  const delegate = DELEGATE_MAP[value] ?? undefined
  modeling.updateProperties(target, {
    'flowable:delegateExpression': delegate,
    delegateExpression: delegate,
  })

  // Seed required defaults so the delegate never throws "Required field not set"
  if (value === 'SEND_NOTIFICATION') {
    if (!readFlowableField(target, 'channel')) {
      writeFlowableField(target, modeling, 'channel', 'email')
    }
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
      getOptions: () => ACTION_TYPES.map(t => ({ value: t.value, label: translate(t.label) })),
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
      {
        id: 'sub-processKey',
        element,
        component: SelectEntry,
        isEdited: isSelectEntryEdited,
        label: translate('Process Key'),
        description: translate('Select the subprocess definition to invoke'),
        getValue: () => readFlowableField(element, 'processKey'),
        setValue: (value: string) => writeFlowableField(element, modeling, 'processKey', value),
        getOptions: () => {
          const options: Array<{ value: string; label: string }> = [
            { value: '', label: translate('(select process)') },
          ]
          for (const def of processDefinitionOptions) {
            options.push({ value: def.key, label: def.name || def.key })
          }
          return options
        },
      },
      flowableFieldEntry(element, modeling, translate, debounce, 'sub-inVariables',
        translate('In Variables (comma-separated)'), 'inVariables'),
      flowableFieldEntry(element, modeling, translate, debounce, 'sub-outVariables',
        translate('Out Variables (comma-separated)'), 'outVariables'),
    )
  }

  if (actionType === 'DMN_ROUTE') {
    entries.push(
      {
        id: 'dmn-decisionRef',
        element,
        component: SelectEntry,
        isEdited: isSelectEntryEdited,
        label: translate('Decision Reference'),
        description: translate('DMN decision key to evaluate for routing'),
        getValue: () => readFlowableField(element, 'decisionRef'),
        setValue: (value: string) => writeFlowableField(element, modeling, 'decisionRef', value),
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
    )
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
        getValue: () => readFlowableField(element, 'confirmationRequired') || 'true',
        setValue: (value: string) => writeFlowableField(element, modeling, 'confirmationRequired', value),
        getOptions: () => [
          { value: 'true',  label: translate('Yes') },
          { value: 'false', label: translate('No') },
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
  let ext = bo.extensionElements
  if (!ext) {
    ext = moddle.create('bpmn:ExtensionElements', { values: [] })
    modeling.updateProperties(element, { extensionElements: ext })
  }
  const values: any[] = ext.get('values') ?? []
  const filtered = values.filter(
    (v: any) => !(v.$type === 'flowable:Field' && v.name === fieldName))
  if (value) {
    const isExpression = /^\$\{.+\}$/.test(value.trim())
    // @ts-ignore
    const field = isExpression
      ? moddle.create('flowable:Field', { name: fieldName, expression: value })
      : moddle.create('flowable:Field', { name: fieldName, string: value })
    filtered.push(field)
  }
  ext.set('values', filtered)
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
      { value: 'slack',    label: translate('Slack (coming soon)') },
      { value: 'whatsapp', label: translate('WhatsApp (coming soon)') },
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
