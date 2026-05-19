'use client'

import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  getApplicableActionTypes,
  setActionType,
} from '@/lib/bpmn/flowable-properties-provider'
import HumanApprovalSection from './sections/HumanApprovalSection'
import SendNotificationSection from './sections/SendNotificationSection'
import ConnectorOperationSection from './sections/ConnectorOperationSection'
import CallSubprocessSection from './sections/CallSubprocessSection'
import SetVariablesSection from './sections/SetVariablesSection'
import ManualStepSection from './sections/ManualStepSection'
import AdvancedExecutionSection from './sections/AdvancedExecutionSection'

interface ServiceTaskPropertiesPanelProps {
  element: any
  modeler: any
  onShowNativePanel?: () => void
}

export default function ServiceTaskPropertiesPanel({
  element,
  modeler,
  onShowNativePanel,
}: ServiceTaskPropertiesPanelProps) {
  const applicableActionTypes = getApplicableActionTypes(element)

  const currentActionType: string =
    element.businessObject?.get?.('flowable:actionType') ??
    element.businessObject?.['flowable:actionType'] ??
    ''

  const handleActionTypeChange = (value: string) => {
    queueMicrotask(() => {
      const modeling = modeler.get('modeling')
      setActionType(element, modeling, value, modeler)
    })
  }

  return (
    <div className="space-y-3 p-2">
      {onShowNativePanel && (
        <button
          type="button"
          onClick={onShowNativePanel}
          className="flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground transition-colors"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M15 18l-6-6 6-6"/>
          </svg>
          General properties
        </button>
      )}

      {applicableActionTypes.length > 1 && (
        <Card>
          <CardHeader className="pb-2 pt-3 px-3 bg-muted border-b">
            <CardTitle className="text-xs font-semibold">Action Type</CardTitle>
          </CardHeader>
          <CardContent className="px-3 pb-3 pt-2">
            <Select value={currentActionType || '__none__'} onValueChange={v => handleActionTypeChange(v === '__none__' ? '' : v)}>
              <SelectTrigger className="h-8 text-xs">
                <SelectValue placeholder="(none)" />
              </SelectTrigger>
              <SelectContent>
                {applicableActionTypes.map(t => (
                  <SelectItem key={t.value || '__none__'} value={t.value || '__none__'}>
                    {t.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </CardContent>
        </Card>
      )}

      {currentActionType === 'HUMAN_APPROVAL' && (
        <HumanApprovalSection element={element} modeler={modeler} />
      )}

      {currentActionType === 'SEND_NOTIFICATION' && (
        <SendNotificationSection element={element} modeler={modeler} />
      )}

      {currentActionType === 'CONNECTOR_OPERATION' && (
        <ConnectorOperationSection element={element} modeler={modeler} />
      )}

      {currentActionType === 'CALL_SUBPROCESS' && (
        <CallSubprocessSection element={element} modeler={modeler} />
      )}

      {currentActionType === 'SET_VARIABLES' && (
        <SetVariablesSection element={element} modeler={modeler} />
      )}

      {currentActionType === 'MANUAL_STEP' && (
        <ManualStepSection element={element} modeler={modeler} />
      )}

      <AdvancedExecutionSection element={element} modeler={modeler} />
    </div>
  )
}
