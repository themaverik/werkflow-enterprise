'use client'

import { useEffect, useState } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { X } from 'lucide-react'
import { writeFlowableField } from '@/lib/bpmn/extension-elements'
import { readVarFields } from '@/lib/bpmn/action-block-logic'

interface SetVariablesSectionProps {
  element: any
  modeler: any
}

export default function SetVariablesSection({ element, modeler }: SetVariablesSectionProps) {
  const [svRows, setSvRows] = useState<Array<{ name: string; value: string }>>([])

  useEffect(() => {
    if (!element || !modeler) return
    setSvRows(readVarFields(element))
  }, [element, modeler])

  return (
    <Card>
      <CardHeader className="pb-2 pt-3 px-3 bg-muted border-b">
        <CardTitle className="text-xs font-semibold">Variable Assignments</CardTitle>
      </CardHeader>
      <CardContent className="px-3 pb-3 pt-2 space-y-2">
        {svRows.length === 0 && (
          <p className="text-xs text-muted-foreground">No variable assignments. Add one below.</p>
        )}
        {svRows.map((row, idx) => (
          <div key={idx} className="flex gap-1 items-center">
            <label htmlFor={`sv-name-${idx}`} className="sr-only">Variable name {idx + 1}</label>
            <Input
              id={`sv-name-${idx}`}
              value={row.name}
              onChange={e => {
                const updated = [...svRows]
                updated[idx] = { ...updated[idx], name: e.target.value }
                setSvRows(updated)
              }}
              onBlur={() => {
                const modeling = modeler.get('modeling')
                const old = svRows[idx]
                if (old.name.trim()) writeFlowableField(element, modeling, `var.${old.name}`, '')
                if (row.name.trim()) writeFlowableField(element, modeling, `var.${row.name}`, row.value)
              }}
              className="h-7 text-xs flex-1"
              placeholder="varName"
            />
            <label htmlFor={`sv-value-${idx}`} className="sr-only">Variable value {idx + 1}</label>
            <Input
              id={`sv-value-${idx}`}
              value={row.value}
              onChange={e => {
                const updated = [...svRows]
                updated[idx] = { ...updated[idx], value: e.target.value }
                setSvRows(updated)
              }}
              onBlur={() => {
                if (row.name.trim()) {
                  writeFlowableField(element, modeler.get('modeling'), `var.${row.name}`, row.value)
                }
              }}
              className="h-7 text-xs flex-1 font-mono"
              placeholder="value or ${expr}"
            />
            <Button
              variant="ghost"
              size="sm"
              className="h-7 w-7 p-0 text-destructive shrink-0"
              aria-label={`Remove variable row ${idx + 1}${row.name ? ` (${row.name})` : ''}`}
              onClick={() => {
                const modeling = modeler.get('modeling')
                if (row.name.trim()) writeFlowableField(element, modeling, `var.${row.name}`, '')
                setSvRows(svRows.filter((_, i) => i !== idx))
              }}
            >
              <X className="h-3 w-3" />
            </Button>
          </div>
        ))}
        <Button
          variant="outline"
          size="sm"
          className="w-full h-7 text-xs"
          onClick={() => setSvRows([...svRows, { name: '', value: '' }])}
        >
          + Add Variable
        </Button>
      </CardContent>
    </Card>
  )
}
