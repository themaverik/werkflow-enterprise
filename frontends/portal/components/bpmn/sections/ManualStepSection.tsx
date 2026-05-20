'use client'

import { useEffect, useState } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  readFlowableField,
  writeFlowableField,
} from '@/lib/bpmn/extension-elements'

interface ManualStepSectionProps {
  element: any
  modeler: any
}

export default function ManualStepSection({ element, modeler }: ManualStepSectionProps) {
  const [msDescription, setMsDescription] = useState('')

  useEffect(() => {
    if (!element || !modeler) return
    setMsDescription(readFlowableField(element, 'stepDescription'))
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
      </CardContent>
    </Card>
  )
}
