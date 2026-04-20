'use client'

import { useEffect, useImperativeHandle, useRef, forwardRef } from 'react'

// dmn-js ships its own CSS — import all three layers
import 'dmn-js/dist/assets/dmn-js-shared.css'
import 'dmn-js/dist/assets/dmn-js-drd.css'
import 'dmn-js/dist/assets/dmn-js-decision-table.css'
import 'dmn-js/dist/assets/dmn-font/css/dmn-embedded.css'

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
}

/**
 * React wrapper for dmn-js DmnModeler.
 * Uses an imperative handle so parent components can call getXml() to extract XML on save/deploy.
 */
const DmnEditor = forwardRef<DmnEditorHandle, DmnEditorProps>(function DmnEditor(
  { xml, readOnly = false, onChange },
  ref
) {
  const containerRef = useRef<HTMLDivElement>(null)
  const modelerRef = useRef<any>(null)

  useImperativeHandle(ref, () => ({
    async getXml(): Promise<string> {
      if (!modelerRef.current) throw new Error('DMN editor not initialised')
      const { xml: result } = await modelerRef.current.saveXML({ format: true })
      return result
    },
  }))

  useEffect(() => {
    if (!containerRef.current) return

    let DmnModeler: any
    let modeler: any

    async function init() {
      // Dynamic import keeps dmn-js out of the server bundle
      const mod = await import('dmn-js/lib/Modeler')
      DmnModeler = mod.default

      modeler = new DmnModeler({
        container: containerRef.current,
      })
      modelerRef.current = modeler

      const initialXml = xml || DEFAULT_EMPTY_DMN

      try {
        await modeler.importXML(initialXml)
      } catch (err) {
        console.error('Failed to import DMN XML:', err)
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
      if (modeler) {
        modeler.destroy()
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
    <div
      ref={containerRef}
      style={{ width: '100%', height: '600px', border: '1px solid #e2e8f0', borderRadius: '8px' }}
    />
  )
})

export default DmnEditor

// ---- minimal valid DMN 1.3 used when creating a new decision ----
const DEFAULT_EMPTY_DMN = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
             id="new_decision_definitions"
             name="New Decision"
             namespace="http://werkflow.com/dmn">
  <decision id="new_decision" name="New Decision">
    <decisionTable id="new_decision_table" hitPolicy="FIRST">
      <input id="input_1" label="Input">
        <inputExpression id="input_1_expr" typeRef="string">
          <text>inputVariable</text>
        </inputExpression>
      </input>
      <output id="output_1" label="Output" name="outputVariable" typeRef="string"/>
    </decisionTable>
  </decision>
</definitions>`
