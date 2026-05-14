import { h, Component } from 'preact'
import { createRoot } from 'react-dom/client'
import type { Root } from 'react-dom/client'
import { createElement } from 'react'
import { VariableComboBoxBpmnAdapter } from './VariableComboBoxBpmnAdapter'
import type { VariableComboBoxBpmnAdapterProps } from './VariableComboBoxBpmnAdapter'

// ── Props ─────────────────────────────────────────────────────────────────────

interface Props {
  id: string
  mode: 'multi' | 'single'
  getValue: () => string
  setValue: (v: string) => void
  sourceKeys: string[]
  processId?: string
  activityId?: string
  placeholder?: string
  keys?: boolean
  label?: string
  element?: unknown
}

// ── Preact shell ──────────────────────────────────────────────────────────────

/**
 * Thin Preact class component that acts as a mount point for the React-based
 * VariableComboBoxBpmnAdapter. All interactive state and React hooks live
 * inside the adapter, completely isolated from bpmn-js's bundled Preact copy.
 *
 * bpmn-js renders this class and controls its lifecycle via its own Preact
 * instance. By keeping this shell hook-free and delegating rendering to a
 * React root, we avoid the `__H` TypeError that occurs when React hooks are
 * called under a foreign Preact runtime.
 */
export class VariableComboBoxEntry extends Component<Props> {
  private _container: HTMLDivElement | null = null
  private _root: Root | null = null

  private _buildAdapterProps(): VariableComboBoxBpmnAdapterProps {
    return {
      mode: this.props.mode,
      getValue: this.props.getValue,
      setValue: this.props.setValue,
      sourceKeys: this.props.sourceKeys,
      processId: this.props.processId,
      activityId: this.props.activityId,
      placeholder: this.props.placeholder,
      keys: this.props.keys,
      label: this.props.label,
    }
  }

  private _renderReact(): void {
    if (!this._container) return
    if (!this._root) {
      this._root = createRoot(this._container)
    }
    this._root.render(
      createElement(VariableComboBoxBpmnAdapter, this._buildAdapterProps())
    )
  }

  componentDidMount(): void {
    this._renderReact()
  }

  componentDidUpdate(): void {
    this._renderReact()
  }

  componentWillUnmount(): void {
    this._root?.unmount()
    this._root = null
  }

  render() {
    return h('div', {
      ref: (el: HTMLDivElement | null) => {
        this._container = el
      },
    })
  }
}

export default VariableComboBoxEntry
