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

/**
 * Module-level variable for group options.
 * Set from BpmnDesigner.tsx after fetching from API.
 */
let groupOptions: Array<{ id: string; name: string }> = []

export function setGroupOptions(options: Array<{ id: string; name: string }>) {
  groupOptions = options
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
        const actionBlockEntries = buildActionBlockEntries(element, modeling, translate, debounce)
        groups.splice(generalIdx + 1, 0, {
          id: 'action-block',
          label: 'Action Block',
          entries: actionBlockEntries,
        })
      }

      // --- User Task ---
      if (is(element, 'bpmn:UserTask')) {
        groups.splice(generalIdx + 1, 0, {
          id: 'flowable-assignment',
          label: 'Assignment',
          entries: [
            {
              id: 'assignee',
              element,
              component: TextFieldEntry,
              isEdited: isTextFieldEntryEdited,
              debounce,
              label: translate('Assignee'),
              description: translate('User ID or expression (e.g., ${initiator})'),
              getValue: () => element.businessObject.assignee || '',
              setValue: (value: string) =>
                modeling.updateProperties(element, { assignee: value || undefined }),
            },
            {
              id: 'candidateUsers',
              element,
              component: TextFieldEntry,
              isEdited: isTextFieldEntryEdited,
              debounce,
              label: translate('Candidate Users'),
              description: translate('Comma-separated user IDs (e.g., user1,user2)'),
              getValue: () => element.businessObject.candidateUsers || '',
              setValue: (value: string) =>
                modeling.updateProperties(element, { candidateUsers: value || undefined }),
            },
            {
              id: 'candidateGroups',
              element,
              component: TextFieldEntry,
              isEdited: isTextFieldEntryEdited,
              debounce,
              label: translate('Candidate Groups'),
              description: translate('Comma-separated group IDs (e.g., HR_ADMIN,MANAGER)'),
              getValue: () => element.businessObject.candidateGroups || '',
              setValue: (value: string) =>
                modeling.updateProperties(element, { candidateGroups: value || undefined }),
            },
          ],
        })

        groups.splice(generalIdx + 2, 0, {
          id: 'flowable-forms',
          label: 'Forms',
          entries: [formKeyEntry(element, modeling, translate)],
        })

        groups.splice(generalIdx + 3, 0, {
          id: 'flowable-task',
          label: 'Task Configuration',
          entries: [
            {
              id: 'priority',
              element,
              component: TextFieldEntry,
              isEdited: isTextFieldEntryEdited,
              debounce,
              label: translate('Priority'),
              description: translate('Task priority (0-100 or expression)'),
              getValue: () => element.businessObject.priority || '',
              setValue: (value: string) =>
                modeling.updateProperties(element, { priority: value || undefined }),
            },
            {
              id: 'dueDate',
              element,
              component: TextFieldEntry,
              isEdited: isTextFieldEntryEdited,
              debounce,
              label: translate('Due Date'),
              description: translate("ISO date or expression (e.g., ${now() + duration('P7D')})"),
              getValue: () => element.businessObject.dueDate || '',
              setValue: (value: string) =>
                modeling.updateProperties(element, { dueDate: value || undefined }),
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

      // --- Signal Events (Flowable-specific: correlation key for targeted delivery) ---
      if (hasSignalDefinition(element)) {
        groups.splice(generalIdx + 1, 0, {
          id: 'flowable-signal',
          label: 'Signal (Flowable)',
          entries: [
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

      // --- Service Task ---
      if (is(element, 'bpmn:ServiceTask')) {
        groups.splice(generalIdx + 1, 0, {
          id: 'flowable-service-task',
          label: 'Service Configuration',
          entries: [
            {
              id: 'delegateExpression',
              element,
              component: TextFieldEntry,
              isEdited: isTextFieldEntryEdited,
              debounce,
              label: translate('Delegate Expression'),
              description: translate('Spring bean expression (e.g., ${restServiceDelegate})'),
              getValue: () =>
                element.businessObject.get('flowable:delegateExpression') ||
                element.businessObject.delegateExpression ||
                '',
              setValue: (value: string) =>
                modeling.updateProperties(element, {
                  'flowable:delegateExpression': value || undefined,
                  delegateExpression: value || undefined,
                }),
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
  { value: 'HUMAN_APPROVAL', label: 'Human Approval' },
  { value: 'NOTIFICATION', label: 'Notification' },
  { value: 'EXTERNAL_API_CALL', label: 'External API Call' },
]

const ACTION_COLOURS: Record<string, { fill: string; stroke: string }> = {
  HUMAN_APPROVAL:    { fill: '#e3f2fd', stroke: '#1565c0' },
  NOTIFICATION:      { fill: '#fff3e0', stroke: '#e65100' },
  EXTERNAL_API_CALL: { fill: '#f3e5f5', stroke: '#6a1b9a' },
}

const DELEGATE_MAP: Record<string, string> = {
  NOTIFICATION:       '${emailActionDelegate}',
  EXTERNAL_API_CALL:  '${externalApiCallDelegate}',
}

function getActionType(element: any): string {
  return element.businessObject.get('flowable:actionType') || ''
}

function setActionType(element: any, modeling: any, value: string) {
  modeling.updateProperties(element, { 'flowable:actionType': value || undefined })

  const delegate = DELEGATE_MAP[value] ?? undefined
  modeling.updateProperties(element, {
    'flowable:delegateExpression': delegate,
    delegateExpression: delegate,
  })

  if (value && ACTION_COLOURS[value]) {
    modeling.setColor(element, ACTION_COLOURS[value])
  } else {
    modeling.setColor(element, { fill: '#f9f9f9', stroke: '#bbb' })
  }
}

function buildActionBlockEntries(
  element: any,
  modeling: any,
  translate: (s: string) => string,
  debounce: any
): any[] {
  const entries: any[] = [
    {
      id: 'actionType',
      element,
      component: SelectEntry,
      isEdited: isSelectEntryEdited,
      label: translate('Action Type'),
      getValue: () => getActionType(element),
      setValue: (value: string) => setActionType(element, modeling, value),
      getOptions: () => ACTION_TYPES.map(t => ({ value: t.value, label: translate(t.label) })),
    },
  ]

  const actionType = getActionType(element)

  if (actionType === 'HUMAN_APPROVAL') {
    entries.push(
      textField(element, modeling, translate, debounce, 'ab-assignee',
        translate('Assignee Expression'), 'flowable:assignee',
        translate('User ID or expression, e.g. ${initiator}')),
      candidateGroupsEntry(element, modeling, translate, debounce),
      formKeyEntry(element, modeling, translate),
      // outcomeVariable must be a <flowable:field> extension element (spec 3.2)
      flowableFieldEntry(element, modeling, translate, debounce, 'ab-outcomeVariable',
        translate('Outcome Variable'), 'outcomeVariable'),
    )
  }

  if (actionType === 'NOTIFICATION') {
    entries.push(
      channelSelectEntry(element, modeling, translate),
      textField(element, modeling, translate, debounce, 'ab-recipient',
        translate('Recipient Expression'), 'ab:recipient', '${employee.email}'),
      templateKeySelectEntry(element, modeling, translate),
      textField(element, modeling, translate, debounce, 'ab-condition',
        translate('Condition (optional)'), 'ab:condition', null),
    )
  }

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

// Writes value as a <flowable:field> extension element (required for fields read by JavaDelegate)
function flowableFieldEntry(
  element: any, modeling: any, translate: (s: string) => string,
  debounce: any, id: string, label: string, fieldName: string
): any {
  const getFlowableFieldValue = (): string => {
    const ext = element.businessObject.extensionElements
    if (!ext) return ''
    const field = ext.get('values')?.find(
      (v: any) => v.$type === 'flowable:Field' && v.name === fieldName)
    return field?.string ?? ''
  }

  const setFlowableFieldValue = (value: string) => {
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
      // @ts-ignore
      const field = moddle.create('flowable:Field', { name: fieldName, string: value })
      filtered.push(field)
    }
    ext.set('values', filtered)
  }

  return {
    id,
    element,
    component: TextFieldEntry,
    isEdited: isTextFieldEntryEdited,
    debounce,
    label,
    getValue: getFlowableFieldValue,
    setValue: setFlowableFieldValue,
  }
}

function candidateGroupsEntry(
  element: any, modeling: any, translate: (s: string) => string, debounce: any
): any {
  const availableGroupNames = groupOptions.map(g => g.name).join(', ')
  const hint = availableGroupNames
    ? translate(`Groups who can claim this task. Comma-separated IDs, e.g. ${availableGroupNames.split(', ').slice(0, 2).join(',')}`)
    : translate('Comma-separated group IDs, e.g. HR_TEAM,FINANCE_TEAM. Any member of these groups will see the task in their queue.')
  return {
    id: 'ab-candidateGroups',
    element,
    component: TextFieldEntry,
    isEdited: isTextFieldEntryEdited,
    debounce,
    label: translate('Candidate Groups'),
    description: hint,
    getValue: () => element.businessObject.get('flowable:candidateGroups') || '',
    setValue: (value: string) =>
      modeling.updateProperties(element, { 'flowable:candidateGroups': value || undefined }),
  }
}

function templateKeySelectEntry(element: any, modeling: any, translate: (s: string) => string): any {
  return {
    id: 'ab-templateKey',
    element,
    component: SelectEntry,
    isEdited: isSelectEntryEdited,
    label: translate('Template Key'),
    getValue: () => element.businessObject.get('ab:templateKey') || '',
    setValue: (value: string) =>
      modeling.updateProperties(element, { 'ab:templateKey': value || undefined }),
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
    getValue: () => element.businessObject.get('ab:channel') || 'email',
    setValue: (value: string) =>
      modeling.updateProperties(element, { 'ab:channel': value }),
    getOptions: () => [{ value: 'email', label: translate('Email') }],
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
