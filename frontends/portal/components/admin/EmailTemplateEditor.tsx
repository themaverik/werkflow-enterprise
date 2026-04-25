'use client'

import { useRef, forwardRef, useImperativeHandle } from 'react'
import dynamic from 'next/dynamic'

// Dynamically import to avoid SSR issues (react-email-editor uses window)
const EmailEditor = dynamic(() => import('react-email-editor'), { ssr: false })

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

export interface EmailTemplateEditorRef {
  exportHtml: () => Promise<{ html: string; design: string }>
}

interface Props {
  initialDesign?: string | null
  formFields?: string[]
}

const EmailTemplateEditor = forwardRef<EmailTemplateEditorRef, Props>(
  ({ initialDesign, formFields = [] }, ref) => {
    const editorRef = useRef<any>(null)

    useImperativeHandle(ref, () => ({
      exportHtml: () =>
        new Promise((resolve, reject) => {
          const editor = editorRef.current?.editor
          if (!editor) {
            reject(new Error('Editor not ready'))
            return
          }
          editor.exportHtml((data: { html: string; design: object }) => {
            resolve({ html: data.html, design: JSON.stringify(data.design) })
          })
        }),
    }))

    const formMergeTags = formFields.map(field => ({
      name: `Form: ${field}`,
      value: `{{${field}}}`,
    }))

    const allMergeTags = [...STANDARD_MERGE_TAGS, ...formMergeTags]

    const onReady = () => {
      const editor = editorRef.current?.editor
      if (!editor) return
      if (initialDesign) {
        try {
          editor.loadDesign(JSON.parse(initialDesign))
        } catch {
          // invalid JSON — start blank
        }
      }
    }

    return (
      <div className="w-full" style={{ height: '600px' }}>
        <EmailEditor
          ref={editorRef}
          onReady={onReady}
          options={{
            mergeTags: allMergeTags,
            displayMode: 'email',
            features: {
              sendTestEmail: false,
              textEditor: { tables: true },
            },
          } as any}
          style={{ height: '100%', minHeight: '600px' }}
        />
      </div>
    )
  }
)

EmailTemplateEditor.displayName = 'EmailTemplateEditor'
export default EmailTemplateEditor
