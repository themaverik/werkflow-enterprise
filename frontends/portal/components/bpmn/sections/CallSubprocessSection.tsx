'use client'

import { useEffect, useState } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

interface CallSubprocessSectionProps {
  element: any
  modeler: any
}

export default function CallSubprocessSection({ element, modeler }: CallSubprocessSectionProps) {
  const [subCalledElement, setSubCalledElement] = useState('')
  const [processDefinitions, setProcessDefinitions] = useState<Array<{ key: string; name: string }>>([])
  const [processDefsLoading, setProcessDefsLoading] = useState(true)

  useEffect(() => {
    if (!element || !modeler) return
    setSubCalledElement(element.businessObject.get('calledElement') || '')
  }, [element, modeler])

  useEffect(() => {
    import('@/lib/bpmn/flowable-properties-provider')
      .then(({ getProcessDefinitionOptions }) => {
        setProcessDefinitions(getProcessDefinitionOptions())
      })
      .catch(err => {
        console.error('Failed to load process definitions', err)
      })
      .finally(() => setProcessDefsLoading(false))
  }, [])

  return (
    <Card>
      <CardHeader className="pb-2 pt-3 px-3 bg-muted border-b">
        <CardTitle className="text-xs font-semibold">Call Subprocess</CardTitle>
      </CardHeader>
      <CardContent className="px-3 pb-3 pt-2 space-y-3">
        <div className="space-y-1">
          <Label className="text-xs">Process Key (calledElement)</Label>
          <Select
            value={subCalledElement || '__none__'}
            onValueChange={v => {
              const val = v === '__none__' ? '' : v
              setSubCalledElement(val)
              modeler.get('modeling').updateProperties(element, { calledElement: val || undefined })
            }}
          >
            <SelectTrigger className="h-8 text-xs">
              <SelectValue placeholder="(select process)" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="__none__">(select process)</SelectItem>
              {processDefinitions.map(d => (
                <SelectItem key={d.key} value={d.key}>{d.name || d.key}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          {processDefinitions.length === 0 && !processDefsLoading && (
            <p className="text-xs text-muted-foreground mt-1">
              No subprocesses available. Deploy a process first.
            </p>
          )}
          <p className="text-xs text-muted-foreground">
            Writes native <code>calledElement</code> attribute — Flowable CallActivity semantics.
          </p>
        </div>
      </CardContent>
    </Card>
  )
}
