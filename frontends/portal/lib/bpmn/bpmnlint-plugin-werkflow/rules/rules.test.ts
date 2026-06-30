'use strict'

import { describe, it, expect } from 'vitest'

// ---------------------------------------------------------------------------
// Minimal mock-node helpers
// ---------------------------------------------------------------------------

interface Reporter {
  report: (id: string, message: string) => void
}

interface FlowableField {
  $type: 'flowable:Field'
  name: string
  string?: string
  expression?: string
}

interface MockNode {
  id: string
  $instanceOf: (type: string) => boolean
  get?: (key: string) => unknown
  outgoing?: Array<{ conditionExpression?: string }>
  default?: unknown
  $parent?: { flowElements?: MockNode[] }
  extensionElements?: {
    get?: (key: string) => unknown[]
    values?: FlowableField[]
  }
  $attrs?: Record<string, string>
  attachedToRef?: MockNode
  eventDefinitions?: MockNode[]
}

/** Creates a node that matches exactly one BPMN type string. */
function makeNode(bpmnType: string, extras: Partial<MockNode> = {}): MockNode {
  return { id: 'node-1', $instanceOf: (t: string) => t === bpmnType, ...extras }
}

/** Collects reporter.report() calls for assertion. */
function makeReporter() {
  const calls: Array<[string, string]> = []
  const reporter: Reporter = { report: (id, msg) => calls.push([id, msg]) }
  return { reporter, calls }
}

/** Builds a mock extensionElements carrying flowable:Field entries. */
function makeExtensionElements(
  fields: Array<{ name: string; value: string }>,
): MockNode['extensionElements'] {
  const values: FlowableField[] = fields.map(({ name, value }) => ({
    $type: 'flowable:Field',
    name,
    string: value,
  }))
  return { get: (key: string) => (key === 'values' ? values : []) }
}

// ---------------------------------------------------------------------------
// Rule factories — CJS modules; vitest/esbuild resolves the interop
// ---------------------------------------------------------------------------

// eslint-disable-next-line @typescript-eslint/no-require-imports
const candidateGroupFallbackFactory = require('./candidate-group-fallback') as () => {
  check: (node: unknown, reporter: Reporter) => void
}
// eslint-disable-next-line @typescript-eslint/no-require-imports
const gatewayDefaultFlowFactory = require('./gateway-default-flow') as () => {
  check: (node: unknown, reporter: Reporter) => void
}
// eslint-disable-next-line @typescript-eslint/no-require-imports
const boundaryTimerSlaCoverageFactory = require('./boundary-timer-sla-coverage') as () => {
  check: (node: unknown, reporter: Reporter) => void
}
// eslint-disable-next-line @typescript-eslint/no-require-imports
const actionBlockCompletenessFactory = require('./action-block-completeness') as () => {
  check: (node: unknown, reporter: Reporter) => void
}
// eslint-disable-next-line @typescript-eslint/no-require-imports
const taskTypeAdvisoryFactory = require('./task-type-advisory') as () => {
  check: (node: unknown, reporter: Reporter) => void
}

const { check: candidateCheck }  = candidateGroupFallbackFactory()
const { check: gatewayCheck }    = gatewayDefaultFlowFactory()
const { check: boundaryCheck }   = boundaryTimerSlaCoverageFactory()
const { check: actionCheck }     = actionBlockCompletenessFactory()
const { check: taskCheck }       = taskTypeAdvisoryFactory()

// ---------------------------------------------------------------------------
// candidate-group-fallback
// ---------------------------------------------------------------------------

describe('candidate-group-fallback', () => {
  it('does not report when HUMAN_APPROVAL task has candidateGroups', () => {
    const { reporter, calls } = makeReporter()
    const node = makeNode('bpmn:UserTask', {
      get: (key: string) =>
        ({ 'flowable:actionType': 'HUMAN_APPROVAL', candidateGroups: 'managers' })[key],
    })
    candidateCheck(node, reporter)
    expect(calls).toHaveLength(0)
  })

  it('reports when HUMAN_APPROVAL task has neither assignee nor candidateGroups', () => {
    const { reporter, calls } = makeReporter()
    const node = makeNode('bpmn:UserTask', {
      get: (key: string) =>
        ({ 'flowable:actionType': 'HUMAN_APPROVAL' })[key as string] ?? undefined,
    })
    candidateCheck(node, reporter)
    expect(calls).toHaveLength(1)
    expect(calls[0][1]).toMatch(/no assignee or candidate groups/i)
  })

  it('ignores non-UserTask elements', () => {
    const { reporter, calls } = makeReporter()
    candidateCheck(makeNode('bpmn:ServiceTask'), reporter)
    expect(calls).toHaveLength(0)
  })

  it('ignores UserTasks that are not HUMAN_APPROVAL', () => {
    const { reporter, calls } = makeReporter()
    const node = makeNode('bpmn:UserTask', {
      get: (key: string) =>
        ({ 'flowable:actionType': 'SEND_NOTIFICATION' })[key as string] ?? undefined,
    })
    candidateCheck(node, reporter)
    expect(calls).toHaveLength(0)
  })
})

// ---------------------------------------------------------------------------
// gateway-default-flow
// ---------------------------------------------------------------------------

describe('gateway-default-flow', () => {
  it('does not report when diverging exclusive gateway has a conditional flow AND a default', () => {
    const { reporter, calls } = makeReporter()
    const defaultFlow = { conditionExpression: undefined }
    const conditionalFlow = { conditionExpression: '${amount > 1000}' }
    const node = makeNode('bpmn:ExclusiveGateway', {
      outgoing: [conditionalFlow, defaultFlow],
      default: defaultFlow,
    })
    gatewayCheck(node, reporter)
    expect(calls).toHaveLength(0)
  })

  it('reports when diverging exclusive gateway has conditional flows but no default', () => {
    const { reporter, calls } = makeReporter()
    const node = makeNode('bpmn:ExclusiveGateway', {
      outgoing: [
        { conditionExpression: '${amount > 1000}' },
        { conditionExpression: '${amount <= 1000}' },
      ],
    })
    gatewayCheck(node, reporter)
    expect(calls).toHaveLength(1)
    expect(calls[0][1]).toMatch(/default flow/i)
  })

  it('does not report when gateway has only one outgoing flow', () => {
    const { reporter, calls } = makeReporter()
    const node = makeNode('bpmn:ExclusiveGateway', {
      outgoing: [{ conditionExpression: '${x}' }],
    })
    gatewayCheck(node, reporter)
    expect(calls).toHaveLength(0)
  })

  it('does not report when no outgoing flow has a condition', () => {
    const { reporter, calls } = makeReporter()
    const node = makeNode('bpmn:ExclusiveGateway', {
      outgoing: [{}, {}],
    })
    gatewayCheck(node, reporter)
    expect(calls).toHaveLength(0)
  })
})

// ---------------------------------------------------------------------------
// boundary-timer-sla-coverage
// ---------------------------------------------------------------------------

describe('boundary-timer-sla-coverage', () => {
  it('does not report when HUMAN_APPROVAL task has an attached timer boundary event', () => {
    const { reporter, calls } = makeReporter()
    const task = makeNode('bpmn:UserTask', {
      get: (key: string) =>
        ({ 'flowable:actionType': 'HUMAN_APPROVAL' })[key as string] ?? undefined,
    })
    const timerDef = makeNode('bpmn:TimerEventDefinition')
    const boundary = makeNode('bpmn:BoundaryEvent', {
      attachedToRef: task,
      eventDefinitions: [timerDef],
    })
    task.$parent = { flowElements: [task, boundary] }
    boundaryCheck(task, reporter)
    expect(calls).toHaveLength(0)
  })

  it('reports when HUMAN_APPROVAL task has no timer boundary event', () => {
    const { reporter, calls } = makeReporter()
    const task = makeNode('bpmn:UserTask', {
      get: (key: string) =>
        ({ 'flowable:actionType': 'HUMAN_APPROVAL' })[key as string] ?? undefined,
      $parent: { flowElements: [] },
    })
    boundaryCheck(task, reporter)
    expect(calls).toHaveLength(1)
    expect(calls[0][1]).toMatch(/timer boundary/i)
  })

  it('does not report when a boundary event exists but is attached to a different task', () => {
    const { reporter, calls } = makeReporter()
    const task = makeNode('bpmn:UserTask', {
      get: (key: string) =>
        ({ 'flowable:actionType': 'HUMAN_APPROVAL' })[key as string] ?? undefined,
    })
    const otherTask = makeNode('bpmn:UserTask', { id: 'other' })
    const timerDef = makeNode('bpmn:TimerEventDefinition')
    const boundary = makeNode('bpmn:BoundaryEvent', {
      attachedToRef: otherTask,
      eventDefinitions: [timerDef],
    })
    task.$parent = { flowElements: [task, boundary] }
    boundaryCheck(task, reporter)
    expect(calls).toHaveLength(1)
  })
})

// ---------------------------------------------------------------------------
// action-block-completeness
// ---------------------------------------------------------------------------

describe('action-block-completeness', () => {
  it('does not report when SEND_NOTIFICATION task has both recipient and templateKey', () => {
    const { reporter, calls } = makeReporter()
    const node = makeNode('bpmn:ServiceTask', {
      get: (key: string) =>
        ({ 'flowable:actionType': 'SEND_NOTIFICATION' })[key as string] ?? undefined,
      extensionElements: makeExtensionElements([
        { name: 'recipient', value: 'user@example.com' },
        { name: 'templateKey', value: 'approval-needed' },
      ]),
    })
    actionCheck(node, reporter)
    expect(calls).toHaveLength(0)
  })

  it('reports when SEND_NOTIFICATION task is missing templateKey', () => {
    const { reporter, calls } = makeReporter()
    const node = makeNode('bpmn:ServiceTask', {
      get: (key: string) =>
        ({ 'flowable:actionType': 'SEND_NOTIFICATION' })[key as string] ?? undefined,
      extensionElements: makeExtensionElements([
        { name: 'recipient', value: 'user@example.com' },
      ]),
    })
    actionCheck(node, reporter)
    expect(calls).toHaveLength(1)
    expect(calls[0][1]).toMatch(/template key/i)
  })

  it('reports when CONNECTOR_OPERATION task has no connector field', () => {
    const { reporter, calls } = makeReporter()
    const node = makeNode('bpmn:ServiceTask', {
      get: (key: string) =>
        ({ 'flowable:actionType': 'CONNECTOR_OPERATION' })[key as string] ?? undefined,
      extensionElements: makeExtensionElements([]),
    })
    actionCheck(node, reporter)
    expect(calls).toHaveLength(1)
    expect(calls[0][1]).toMatch(/connector/i)
  })

  it('does not report when CONNECTOR_OPERATION task has a connector field', () => {
    const { reporter, calls } = makeReporter()
    const node = makeNode('bpmn:ServiceTask', {
      get: (key: string) =>
        ({ 'flowable:actionType': 'CONNECTOR_OPERATION' })[key as string] ?? undefined,
      extensionElements: makeExtensionElements([
        { name: 'connector', value: 'rest-api-connector' },
      ]),
    })
    actionCheck(node, reporter)
    expect(calls).toHaveLength(0)
  })
})

// ---------------------------------------------------------------------------
// task-type-advisory
// ---------------------------------------------------------------------------

describe('task-type-advisory', () => {
  it('does not report on a plain ServiceTask with no dead attrs', () => {
    const { reporter, calls } = makeReporter()
    const node = makeNode('bpmn:ServiceTask', { get: () => undefined })
    taskCheck(node, reporter)
    expect(calls).toHaveLength(0)
  })

  it('reports when the element is a ScriptTask (quarantined per ADR-016)', () => {
    const { reporter, calls } = makeReporter()
    const node = makeNode('bpmn:ScriptTask', { get: () => undefined })
    taskCheck(node, reporter)
    expect(calls).toHaveLength(1)
    expect(calls[0][1]).toMatch(/script task/i)
  })

  it('reports when the element is a BusinessRuleTask (dead in Flowable 7.2)', () => {
    const { reporter, calls } = makeReporter()
    const node = makeNode('bpmn:BusinessRuleTask', { get: () => undefined })
    taskCheck(node, reporter)
    expect(calls).toHaveLength(1)
    expect(calls[0][1]).toMatch(/business rule/i)
  })

  it('reports when the element carries the dead flowable:signalName attribute via $attrs', () => {
    const { reporter, calls } = makeReporter()
    // get() throws (attr not in descriptor) → falls through to $attrs
    const node = makeNode('bpmn:ServiceTask', {
      get: (_key: string) => { throw new Error('not in descriptor') },
      $attrs: { 'flowable:signalName': 'mySignal' },
    })
    taskCheck(node, reporter)
    expect(calls).toHaveLength(1)
    expect(calls[0][1]).toMatch(/flowable:signalName/i)
  })

  it('does not report when a dead attr getter returns undefined (L3: falsy-but-null guard)', () => {
    // After L3 fix: get() returning undefined falls through correctly to $attrs;
    // when $attrs also has no value, nothing should be reported.
    const { reporter, calls } = makeReporter()
    const node = makeNode('bpmn:ServiceTask', {
      get: () => undefined,
      $attrs: {},
    })
    taskCheck(node, reporter)
    expect(calls).toHaveLength(0)
  })
})
