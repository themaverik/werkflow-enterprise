'use client'

import { useEffect, useState } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

/** Reads the body of the first flowable:FailedJobRetryTimeCycle extension element, or '' if absent. */
function readRetryCycle(element: any): string {
  const ext = element.businessObject.extensionElements
  if (!ext) return ''
  const retry = ext.get('values')?.find((v: any) => v.$type === 'flowable:FailedJobRetryTimeCycle')
  return retry?.body ?? ''
}

function writeRetryCycle(element: any, modeling: any, value: string) {
  const bo = element.businessObject
  const moddle = bo.$model
  const existingExt = bo.extensionElements
  const existingValues: any[] = existingExt?.get('values') ?? []
  const filtered = existingValues.filter((v: any) => v.$type !== 'flowable:FailedJobRetryTimeCycle')
  if (value.trim()) {
    filtered.push(moddle.create('flowable:FailedJobRetryTimeCycle', { body: value.trim() }))
  }
  modeling.updateProperties(element, {
    extensionElements: filtered.length > 0
      ? moddle.create('bpmn:ExtensionElements', { values: filtered })
      : undefined,
  })
}

interface AdvancedExecutionSectionProps {
  element: any
  modeler: any
}

export default function AdvancedExecutionSection({ element, modeler }: AdvancedExecutionSectionProps) {
  const [isAsync, setIsAsync] = useState<boolean>(false)
  const [isExclusive, setIsExclusive] = useState<boolean>(true)
  const [retryCycle, setRetryCycle] = useState<string>('')

  useEffect(() => {
    if (!element || !modeler) return
    const bo = element.businessObject
    setIsAsync(bo.async === true)
    setIsExclusive(bo.exclusive !== false)
    setRetryCycle(readRetryCycle(element))
  }, [element, modeler])

  const handleAsyncChange = (value: string) => {
    const next = value === 'true'
    setIsAsync(next)
    const modeling = modeler.get('modeling')
    modeling.updateProperties(element, { async: next || undefined })
  }

  const handleExclusiveChange = (value: string) => {
    const next = value === 'true'
    setIsExclusive(next)
    const modeling = modeler.get('modeling')
    modeling.updateProperties(element, { exclusive: next === false ? false : undefined })
  }

  const handleRetryCycleBlur = () => {
    const modeling = modeler.get('modeling')
    writeRetryCycle(element, modeling, retryCycle)
  }

  return (
    <Card>
      <CardHeader className="pb-2 pt-3 px-3 bg-muted border-b">
        <CardTitle className="text-xs font-semibold">Advanced Execution</CardTitle>
      </CardHeader>
      <CardContent className="px-3 pb-3 pt-2 space-y-2">
        <div>
          <label htmlFor="adv-async" className="sr-only">Async</label>
          <div className="flex items-center gap-2">
            <span className="text-xs text-muted-foreground w-24 shrink-0">Async</span>
            <Select value={isAsync ? 'true' : 'false'} onValueChange={handleAsyncChange}>
              <SelectTrigger id="adv-async" className="h-7 text-xs">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="false">No</SelectItem>
                <SelectItem value="true">Yes</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>

        <div>
          <label htmlFor="adv-exclusive" className="sr-only">Exclusive</label>
          <div className="flex items-center gap-2">
            <span className="text-xs text-muted-foreground w-24 shrink-0">Exclusive</span>
            <Select
              value={isExclusive ? 'true' : 'false'}
              onValueChange={handleExclusiveChange}
              disabled={!isAsync}
            >
              <SelectTrigger id="adv-exclusive" className={`h-7 text-xs ${!isAsync ? 'opacity-50 cursor-not-allowed' : ''}`}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="true">Yes</SelectItem>
                <SelectItem value="false">No</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>

        <div>
          <label htmlFor="adv-retry" className="sr-only">Retry Cycle</label>
          <div className="flex items-center gap-2">
            <span className="text-xs text-muted-foreground w-24 shrink-0">Retry Cycle</span>
            <Input
              id="adv-retry"
              value={retryCycle}
              onChange={e => setRetryCycle(e.target.value)}
              onBlur={handleRetryCycleBlur}
              className="h-7 text-xs font-mono"
              placeholder="e.g. R3/PT1M (3 retries, 1 min apart)"
            />
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
