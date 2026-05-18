'use client'

import { useCallback, useEffect, useState } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { X } from 'lucide-react'
import { VariableComboBoxBpmnAdapter } from '@/components/bpmn/VariableComboBoxBpmnAdapter'
import { writeFlowableField } from '@/lib/bpmn/extension-elements'
import { readVarFields } from '@/lib/bpmn/action-block-logic'

interface SetVariablesSectionProps {
  element: any
  modeler: any
}

type Row = { name: string; value: string }

interface SetVariableRowProps {
  element: any
  modeler: any
  idx: number
  rows: Row[]
  setRows: (rows: Row[]) => void
}

function SetVariableRow({ element, modeler, idx, rows, setRows }: SetVariableRowProps) {
  const row = rows[idx]

  const getValue = useCallback(() => rows[idx]?.value ?? '', [rows, idx])

  const setValue = useCallback((v: string) => {
    const updated = [...rows]
    updated[idx] = { ...updated[idx], value: v }
    setRows(updated)
    if (updated[idx].name.trim()) {
      writeFlowableField(element, modeler.get('modeling'), `var.${updated[idx].name}`, v)
    }
  }, [rows, idx, element, modeler, setRows])

  return (
    <div className="flex gap-1 items-center">
      <label htmlFor={`sv-name-${idx}`} className="sr-only">Variable name {idx + 1}</label>
      <Input
        id={`sv-name-${idx}`}
        value={row.name}
        onChange={e => {
          const updated = [...rows]
          updated[idx] = { ...updated[idx], name: e.target.value }
          setRows(updated)
        }}
        onBlur={() => {
          const modeling = modeler.get('modeling')
          const old = rows[idx]
          if (old.name.trim()) writeFlowableField(element, modeling, `var.${old.name}`, '')
          if (row.name.trim()) writeFlowableField(element, modeling, `var.${row.name}`, row.value)
        }}
        className="h-7 text-xs flex-1"
        placeholder="varName"
      />
      <div className="flex-1">
        <VariableComboBoxBpmnAdapter
          mode="single"
          sourceKeys={['dtds-variables-string', 'dtds-variables-number', 'dtds-variables-date']}
          processId={element.businessObject?.$parent?.id}
          activityId={element.businessObject?.id}
          placeholder="value or ${expr}"
          getValue={getValue}
          setValue={setValue}
        />
      </div>
      <Button
        variant="ghost"
        size="sm"
        className="h-7 w-7 p-0 text-destructive shrink-0"
        aria-label={`Remove variable row ${idx + 1}${row.name ? ` (${row.name})` : ''}`}
        onClick={() => {
          const modeling = modeler.get('modeling')
          if (row.name.trim()) writeFlowableField(element, modeling, `var.${row.name}`, '')
          setRows(rows.filter((_, i) => i !== idx))
        }}
      >
        <X className="h-3 w-3" />
      </Button>
    </div>
  )
}

export default function SetVariablesSection({ element, modeler }: SetVariablesSectionProps) {
  const [svRows, setSvRows] = useState<Row[]>([])

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
        {svRows.map((_, idx) => (
          <SetVariableRow
            key={idx}
            element={element}
            modeler={modeler}
            idx={idx}
            rows={svRows}
            setRows={setSvRows}
          />
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
