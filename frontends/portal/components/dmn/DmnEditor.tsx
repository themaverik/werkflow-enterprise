'use client'

import { useEffect, useRef } from 'react'

// dmn-js ships its own CSS — import all viewer/editor layers
// diagram-js.css: base overlay/popup positioning (position:fixed z-index:200 for .djs-popup)
import 'dmn-js/dist/assets/diagram-js.css'
import 'dmn-js/dist/assets/dmn-js-shared.css'
import 'dmn-js/dist/assets/dmn-js-drd.css'
import 'dmn-js/dist/assets/dmn-js-decision-table.css'
// decision-table-controls.css: context-menu { position: absolute; z-index: 6 }
// Without this the column-header editor popup is un-positioned (static flow → row 1)
import 'dmn-js/dist/assets/dmn-js-decision-table-controls.css'
import 'dmn-js/dist/assets/dmn-js-literal-expression.css'
import 'dmn-js/dist/assets/dmn-font/css/dmn-embedded.css'
import './dmn-overrides.css'

export interface DmnEditorHandle {
  /** Returns the current DMN XML from the editor */
  getXml(): Promise<string>
}

interface DmnEditorProps {
  /** Initial DMN XML to load into the editor */
  xml?: string
  /** Whether the editor is in read-only mode */
  readOnly?: boolean
  /** Called whenever the DMN XML changes */
  onChange?: (xml: string) => void
  /**
   * Called once the modeler is ready.
   * Use this instead of forwardRef — next/dynamic does not reliably forward refs,
   * so useImperativeHandle never exposes the handle to the parent.
   */
  onInit?: (handle: DmnEditorHandle) => void
}

/**
 * React wrapper for dmn-js DmnModeler.
 * Exposes a getXml() handle via the onInit callback (not forwardRef) so it
 * works correctly when loaded through next/dynamic.
 */
interface DmnModelerInstance {
  importXML: (xml: string) => Promise<{ warnings: unknown[] }>
  saveXML: (options?: { format?: boolean }) => Promise<{ xml: string }>
  destroy: () => void
  on: (event: string, callback: (...args: unknown[]) => void | Promise<void>) => void
}

function DmnEditor({ xml, readOnly = false, onChange, onInit }: DmnEditorProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const modelerRef = useRef<DmnModelerInstance | null>(null)

  useEffect(() => {
    if (!containerRef.current) return

    // cancelled flag prevents a slow async init from writing to modelerRef
    // after StrictMode has already torn down this effect and run cleanup.
    let cancelled = false

    async function init() {
      // Use the pre-bundled UMD dist — webpack handles it correctly as CJS
      // (module.exports = factory()), giving mod.default = Modeler constructor.
      // The lib/Modeler.js path is untranspiled ESM and breaks under Next.js webpack.
      const mod = await import('dmn-js/dist/dmn-modeler.development.js')

      // StrictMode may have already unmounted while we awaited the dynamic import.
      // Bail out to avoid mounting an orphaned instance with no cleanup path.
      if (cancelled) return

      const DmnModeler = mod.default ?? (mod as any)

      const modeler = new DmnModeler({
        container: containerRef.current,
      })
      modelerRef.current = modeler

      const initialXml = xml || DEFAULT_EMPTY_DMN

      try {
        await modeler.importXML(initialXml)
      } catch (err) {
        console.error('Failed to import DMN XML:', err)
      }

      // Expose getXml handle to parent via callback — avoids forwardRef + next/dynamic incompatibility
      if (onInit) {
        onInit({
          async getXml(): Promise<string> {
            if (!modelerRef.current) throw new Error('DMN editor not initialised')
            const { xml: result } = await modelerRef.current.saveXML({ format: true })
            return result
          },
        })
      }

      if (onChange) {
        modeler.on('commandStack.changed', async () => {
          try {
            const { xml: updated } = await modeler.saveXML({ format: true })
            onChange(updated)
          } catch {
            // ignore save errors during live editing
          }
        })
      }

      if (readOnly) {
        // dmn-js does not have a built-in read-only flag on DmnModeler;
        // disable editing by removing all editor interactions via CSS overlay
        if (containerRef.current) {
          containerRef.current.style.pointerEvents = 'none'
        }
      }
    }

    init()

    return () => {
      cancelled = true
      // Destroy and clear the ref regardless of whether init() finished —
      // modelerRef.current is null until the constructor runs, so this is safe.
      if (modelerRef.current) {
        modelerRef.current.destroy()
        modelerRef.current = null
      }
    }
    // xml and readOnly intentionally excluded — reinitialising on every change breaks editing
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Reload XML when the prop changes from outside (e.g. switching between decisions)
  useEffect(() => {
    if (!modelerRef.current || !xml) return
    modelerRef.current.importXML(xml).catch((err: unknown) => {
      console.error('Failed to reload DMN XML:', err)
    })
  }, [xml])

  return (
    // Outer shell: visual border/radius only — must NOT have overflow:hidden
    // because dmn-js absolutely-positions its cell editors and popup menus
    // relative to the inner container. overflow:hidden clips those elements
    // and causes hit-testing to land on the wrong row/cell.
    <div style={{ width: '100%', border: '1px solid #e2e8f0', borderRadius: '8px' }}>
      <div
        ref={containerRef}
        style={{
          position: 'relative',
          width: '100%',
          height: '600px',
        }}
      />
    </div>
  )
}

export default DmnEditor

// ---- minimal valid DMN 1.3 used when creating a new decision ----
const DEFAULT_EMPTY_DMN = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
             id="new_decision_definitions"
             name="New Decision"
             namespace="http://werkflow.com/dmn">
  <decision id="new_decision" name="New Decision">
    <decisionTable id="new_decision_table" hitPolicy="FIRST">
      <input id="input_1" label="Click to set Input">
        <inputExpression id="input_1_expr" typeRef="string">
          <text></text>
        </inputExpression>
      </input>
      <output id="output_1" label="Click to set Output" name="" typeRef="string"/>
    </decisionTable>
  </decision>
</definitions>`
