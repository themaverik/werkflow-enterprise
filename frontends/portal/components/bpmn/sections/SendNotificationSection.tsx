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
import { readFlowableField, writeFlowableField } from '@/lib/bpmn/extension-elements'
import { getNotificationTemplates } from '@/lib/bpmn/flowable-properties-provider'
import { VariableComboBoxBpmnAdapter } from '@/components/bpmn/VariableComboBoxBpmnAdapter'

interface SendNotificationSectionProps {
  element: any
  modeler: any
}

export default function SendNotificationSection({ element, modeler }: SendNotificationSectionProps) {
  const [notifChannel, setNotifChannel] = useState('email')
  const [notifTemplateKey, setNotifTemplateKey] = useState('')
  const [notifCondition, setNotifCondition] = useState('')
  const [notifTemplates, setNotifTemplates] = useState<Array<{ key: string; name: string }>>([])

  useEffect(() => {
    if (!element || !modeler) return
    setNotifChannel(readFlowableField(element, 'channel') || 'email')
    setNotifTemplateKey(readFlowableField(element, 'templateKey'))
    setNotifCondition(readFlowableField(element, 'condition'))
  }, [element, modeler])

  useEffect(() => {
    setNotifTemplates(getNotificationTemplates())
  }, [])

  return (
    <Card>
      <CardHeader className="pb-2 pt-3 px-3 bg-muted border-b">
        <CardTitle className="text-xs font-semibold">Notification</CardTitle>
      </CardHeader>
      <CardContent className="px-3 pb-3 space-y-3">
        <div className="space-y-1">
          <Label className="text-xs">Channel</Label>
          <Select value={notifChannel} onValueChange={v => {
            setNotifChannel(v)
            writeFlowableField(element, modeler.get('modeling'), 'channel', v || 'email')
          }}>
            <SelectTrigger className="h-8 text-xs">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="email">Email</SelectItem>
              <SelectItem value="slack" disabled>Slack (coming soon)</SelectItem>
              <SelectItem value="whatsapp" disabled>WhatsApp (coming soon)</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-1">
          <Label className="text-xs">Recipient</Label>
          <VariableComboBoxBpmnAdapter
            key={`nr-${element.id}`}
            mode="single"
            sourceKeys={['dtds-variables-string']}
            processId={element.businessObject?.$parent?.id}
            activityId={element.businessObject?.id}
            placeholder="${initiator} or email@example.com"
            getValue={() => readFlowableField(element, 'recipient')}
            setValue={(v) => writeFlowableField(element, modeler.get('modeling'), 'recipient', v)}
          />
        </div>
        <div className="space-y-1">
          <Label className="text-xs">Template</Label>
          <Select value={notifTemplateKey || '__none__'} onValueChange={v => {
            const val = v === '__none__' ? '' : v
            setNotifTemplateKey(val)
            writeFlowableField(element, modeler.get('modeling'), 'templateKey', val)
          }}>
            <SelectTrigger className="h-8 text-xs">
              <SelectValue placeholder="(select template)" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="__none__">(select template)</SelectItem>
              {notifTemplates.map(tmpl => (
                <SelectItem key={tmpl.key} value={tmpl.key}>{tmpl.name || tmpl.key}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-1">
          <Label className="text-xs">Condition (optional)</Label>
          <Input
            className="h-8 text-xs font-mono"
            value={notifCondition}
            placeholder="${status == 'approved'}"
            onChange={e => setNotifCondition(e.target.value)}
            onBlur={() => writeFlowableField(element, modeler.get('modeling'), 'condition', notifCondition)}
          />
        </div>
      </CardContent>
    </Card>
  )
}
