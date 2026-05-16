'use client'

import { useEffect, useState } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  readFlowableField,
  writeFlowableField,
} from '@/lib/bpmn/extension-elements'
import { setManualStepConfirmation } from '@/lib/bpmn/action-block-logic'

interface ManualStepSectionProps {
  element: any
  modeler: any
}

export default function ManualStepSection({ element, modeler }: ManualStepSectionProps) {
  const [msDescription, setMsDescription] = useState('')
  const [msConfirmation, setMsConfirmation] = useState('false')

  useEffect(() => {
    if (!element || !modeler) return
    setMsDescription(readFlowableField(element, 'stepDescription'))
    setMsConfirmation(readFlowableField(element, 'confirmationRequired') || 'false')
  }, [element, modeler])

  return (
    <Card>
      <CardHeader className="pb-2 pt-3 px-3 bg-muted border-b">
        <CardTitle className="text-xs font-semibold">Manual Step</CardTitle>
      </CardHeader>
      <CardContent className="px-3 pb-3 pt-2 space-y-3">
        <div className="space-y-1">
          <Label className="text-xs">Step Description</Label>
          <Input
            className="h-8 text-xs"
            value={msDescription}
            placeholder="Instructions shown to the assignee"
            onChange={e => setMsDescription(e.target.value)}
            onBlur={() => writeFlowableField(element, modeler.get('modeling'), 'stepDescription', msDescription)}
          />
        </div>
        <div className="space-y-1">
          <Label className="text-xs">Confirmation Required</Label>
          <Select
            value={msConfirmation}
            onValueChange={v => {
              setMsConfirmation(v)
              setManualStepConfirmation(element, modeler, v)
            }}
          >
            <SelectTrigger className="h-8 text-xs">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="false">No</SelectItem>
              <SelectItem value="true">Yes (morphs to UserTask with confirm form)</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </CardContent>
    </Card>
  )
}
