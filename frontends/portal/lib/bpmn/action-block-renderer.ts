/**
 * ActionBlockRenderer
 *
 * Custom bpmn-js renderer that draws a colour badge overlay on Service Tasks
 * and User Tasks that have a flowable:actionType extension property set.
 */
import BaseRenderer from 'diagram-js/lib/draw/BaseRenderer'
import { is } from 'bpmn-js/lib/util/ModelUtil'

const ACTION_COLOURS: Record<string, { fill: string; stroke: string; label: string }> = {
  HUMAN_APPROVAL:    { fill: '#e3f2fd', stroke: '#1565c0', label: 'APPROVAL' },
  SEND_NOTIFICATION: { fill: '#fff3e0', stroke: '#e65100', label: 'NOTIFY' },
  EXTERNAL_API_CALL: { fill: '#f3e5f5', stroke: '#6a1b9a', label: 'API' },
  CALL_SUBPROCESS:   { fill: '#e8f5e9', stroke: '#2e7d32', label: 'SUB' },
  GROOVY_SCRIPT:     { fill: '#fce4ec', stroke: '#c62828', label: 'SCRIPT' },
  MANUAL_STEP:       { fill: '#f3e5f5', stroke: '#4a148c', label: 'MANUAL' },
}

const SVG_NS = 'http://www.w3.org/2000/svg'

export default class ActionBlockRenderer extends BaseRenderer {
  static $inject = ['eventBus', 'bpmnRenderer']

  private _bpmnRenderer: any

  constructor(eventBus: any, bpmnRenderer: any) {
    super(eventBus, 1500) // priority above default renderer (1000)
    this._bpmnRenderer = bpmnRenderer
  }

  canRender(element: any): boolean {
    if (element.labelTarget) return false
    if (
      !is(element, 'bpmn:Task') &&
      !is(element, 'bpmn:ServiceTask') &&
      !is(element, 'bpmn:UserTask')
    ) {
      return false
    }
    return !!this._getActionType(element)
  }

  drawShape(parentNode: SVGElement, element: any): SVGElement {
    // Let the default renderer draw the base shape first
    const shape = this._bpmnRenderer.drawShape(parentNode, element)

    const actionType = this._getActionType(element)
    if (!actionType) return shape

    const config = ACTION_COLOURS[actionType]
    if (!config) return shape

    this._drawBadge(parentNode, element, config.label, config.stroke)

    return shape
  }

  private _getActionType(element: any): string | null {
    const bo = element.businessObject
    if (!bo) return null
    // Try direct attribute first (set via modeling.updateProperties)
    const direct = bo.get('flowable:actionType')
    if (direct) return direct
    // Fallback: check flowable:Properties extension elements
    const ext = bo.extensionElements
    if (!ext) return null
    const propsEl = ext.values?.find((v: any) => v.$type === 'flowable:Properties')
    if (!propsEl) return null
    return propsEl.values?.find((p: any) => p.name === 'actionType')?.value ?? null
  }

  private _drawBadge(
    parent: SVGElement,
    element: any,
    label: string,
    colour: string
  ): void {
    const { width } = element

    const badge = document.createElementNS(SVG_NS, 'rect')
    badge.setAttribute('x', String(width - 44))
    badge.setAttribute('y', '4')
    badge.setAttribute('width', '40')
    badge.setAttribute('height', '14')
    badge.setAttribute('rx', '3')
    badge.setAttribute('fill', colour)

    const text = document.createElementNS(SVG_NS, 'text')
    text.setAttribute('x', String(width - 24))
    text.setAttribute('y', '14')
    text.setAttribute('text-anchor', 'middle')
    text.setAttribute('fill', '#fff')
    text.setAttribute('font-size', '8')
    text.setAttribute('font-weight', 'bold')
    text.textContent = label

    parent.appendChild(badge)
    parent.appendChild(text)
  }
}
