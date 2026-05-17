'use client'

import { useEffect, useState } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  readFlowableField,
  writeFlowableField,
} from '@/lib/bpmn/extension-elements'
import { VariableComboBoxBpmnAdapter } from '@/components/bpmn/VariableComboBoxBpmnAdapter'

interface HumanApprovalSectionProps {
  element: any
  modeler: any
}

export default function HumanApprovalSection({ element, modeler }: HumanApprovalSectionProps) {
  const [outcomeVar, setOutcomeVar] = useState('')

  useEffect(() => {
    if (!element || !modeler) return
    setOutcomeVar(readFlowableField(element, 'outcomeVariable'))
  }, [element, modeler])

  return (
    <Card>
      <CardHeader className="pb-2 pt-3 px-3 bg-muted border-b">
        <CardTitle className="text-xs font-semibold">Assignment</CardTitle>
      </CardHeader>
      <CardContent className="px-3 pb-3 pt-2 space-y-3">
        <div className="space-y-1">
          <Label className="text-xs">Candidate Groups</Label>
          <VariableComboBoxBpmnAdapter
            key={`cg-${element.id}`}
            mode="multi"
            sourceKeys={['pss-candidate-groups', 'dtds-variables-string', 'custody-feel']}
            processId={element.businessObject?.$parent?.id}
            activityId={element.businessObject?.id}
            getValue={() => element.businessObject.candidateGroups || ''}
            setValue={(v) => modeler.get('modeling').updateProperties(element, { candidateGroups: v || undefined })}
          />
        </div>
        <div className="space-y-1">
          <Label className="text-xs">Assignee</Label>
          <VariableComboBoxBpmnAdapter
            key={`as-${element.id}`}
            mode="single"
            sourceKeys={['role-mappings', 'dtds-variables-string']}
            processId={element.businessObject?.$parent?.id}
            activityId={element.businessObject?.id}
            getValue={() => element.businessObject.assignee || ''}
            setValue={(v) => modeler.get('modeling').updateProperties(element, { assignee: v || undefined })}
          />
        </div>
        <div className="space-y-1">
          <Label className="text-xs">Candidate Users</Label>
          <VariableComboBoxBpmnAdapter
            key={`cu-${element.id}`}
            mode="multi"
            sourceKeys={['dtds-variables-string']}
            processId={element.businessObject?.$parent?.id}
            activityId={element.businessObject?.id}
            getValue={() => element.businessObject.candidateUsers || ''}
            setValue={(v) => modeler.get('modeling').updateProperties(element, { candidateUsers: v || undefined })}
          />
        </div>
        <div className="space-y-1">
          <Label className="text-xs">Form Key</Label>
          <VariableComboBoxBpmnAdapter
            key={`fk-${element.id}`}
            mode="single"
            sourceKeys={['forms-deployed']}
            processId={element.businessObject?.$parent?.id}
            activityId={element.businessObject?.id}
            getValue={() => element.businessObject.formKey || element.businessObject.$attrs?.['flowable:formKey'] || ''}
            setValue={(v) => modeler.get('modeling').updateProperties(element, { formKey: v || undefined })}
          />
        </div>
        <div className="space-y-1">
          <Label className="text-xs">Outcome Variable</Label>
          <Input
            className="h-8 text-xs"
            value={outcomeVar}
            placeholder="approvalOutcome"
            onChange={e => setOutcomeVar(e.target.value)}
            onBlur={() => writeFlowableField(element, modeler.get('modeling'), 'outcomeVariable', outcomeVar)}
          />
        </div>
      </CardContent>
    </Card>
  )
}
