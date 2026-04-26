'use client'

import { useRef } from 'react'
// Import directly — SSR is handled by the page-level dynamic() import of this component.
// Do NOT re-wrap with next/dynamic here: double-wrapping breaks ref forwarding.
import EmailEditor from 'react-email-editor'

export const STANDARD_MERGE_TAGS = [
  { name: 'Initiator Username', value: '{{initiator}}' },
  { name: 'Process Name', value: '{{processName}}' },
  { name: 'Process Instance ID', value: '{{processInstanceId}}' },
  { name: 'Task Name', value: '{{taskName}}' },
  { name: 'Assignee Name', value: '{{assigneeName}}' },
  { name: 'Requester Name', value: '{{requesterName}}' },
  { name: 'Decision', value: '{{decision}}' },
  { name: 'Comment', value: '{{comment}}' },
  { name: 'Start Time', value: '{{startTime}}' },
]

// Imperative API exposed via onReady callback (not ref) to work around
// next/dynamic not forwarding refs through LoadableComponent.
export interface EmailTemplateEditorApi {
  exportHtml: () => Promise<{ html: string; design: string }>
}

interface Props {
  initialDesign?: string | null
  formFields?: string[]
  /** Called once Unlayer is ready — store the returned api to call exportHtml() */
  onReady?: (api: EmailTemplateEditorApi) => void
}

export default function EmailTemplateEditor({ initialDesign, formFields = [], onReady }: Props) {
  const editorInstanceRef = useRef<any>(null)

  const handleReady = (unlayer: any) => {
    editorInstanceRef.current = unlayer

    if (initialDesign) {
      try {
        unlayer.loadDesign(JSON.parse(initialDesign))
      } catch {
        // invalid stored JSON — start with blank canvas
      }
    }

    onReady?.({
      exportHtml: () =>
        new Promise((resolve, reject) => {
          const editor = editorInstanceRef.current
          if (!editor) {
            reject(new Error('Editor not ready — please wait for the editor to load'))
            return
          }
          editor.exportHtml((data: { html: string; design: object }) => {
            resolve({ html: data.html, design: JSON.stringify(data.design) })
          })
        }),
    })
  }

  const formMergeTags = formFields.map(field => ({
    name: `Form: ${field}`,
    value: `{{${field}}}`,
  }))

  return (
    <div style={{ width: '100%', height: '640px' }}>
      <EmailEditor
        onReady={handleReady}
        minHeight={640}
        options={{
          mergeTags: [...STANDARD_MERGE_TAGS, ...formMergeTags],
          displayMode: 'email',
          features: {
            sendTestEmail: false,
            textEditor: { tables: true },
          },
        } as any}
        style={{ width: '100%', height: '640px' }}
      />
    </div>
  )
}
