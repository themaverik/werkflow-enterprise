'use client'

import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { useTranslations } from 'next-intl'
import { Play, X } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { toast } from 'sonner'
import { testDecision, type DmnTestResultDto } from '@/lib/api/dmn'

interface DmnTestPanelProps {
  decisionKey: string
}

interface InputField {
  id: string
  key: string
  value: string
}

/**
 * Ad-hoc decision test panel.
 * Allows business analysts to add variable inputs and evaluate the decision immediately
 * without needing to run a full BPMN process instance.
 */
export default function DmnTestPanel({ decisionKey }: DmnTestPanelProps) {
  const t = useTranslations('decisions')
  const [fields, setFields] = useState<InputField[]>([{ id: '1', key: '', value: '' }])
  const [result, setResult] = useState<DmnTestResultDto | null>(null)

  const testMutation = useMutation({
    mutationFn: () => {
      const inputs: Record<string, unknown> = {}
      for (const field of fields) {
        if (field.key.trim()) {
          // coerce numeric strings to numbers for proper DMN evaluation
          const raw = field.value.trim()
          inputs[field.key.trim()] = isNumeric(raw) ? Number(raw) : raw
        }
      }
      return testDecision(decisionKey, inputs)
    },
    onSuccess: (data) => {
      setResult(data)
    },
    onError: (err: Error) => {
      toast.error(t('editor.testFailed'), { description: err.message })
    },
  })

  function addField() {
    setFields((prev) => [...prev, { id: String(Date.now()), key: '', value: '' }])
  }

  function removeField(id: string) {
    setFields((prev) => prev.filter((f) => f.id !== id))
  }

  function updateField(id: string, prop: 'key' | 'value', value: string) {
    setFields((prev) => prev.map((f) => (f.id === id ? { ...f, [prop]: value } : f)))
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{t('editor.testPanelTitle')}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="space-y-2">
          {fields.map((field) => (
            <div key={field.id} className="flex gap-2 items-center">
              <Input
                placeholder={t('editor.inputKey')}
                value={field.key}
                onChange={(e) => updateField(field.id, 'key', e.target.value)}
                className="flex-1"
              />
              <Input
                placeholder={t('editor.inputValue')}
                value={field.value}
                onChange={(e) => updateField(field.id, 'value', e.target.value)}
                className="flex-1"
              />
              <Button
                variant="ghost"
                size="sm"
                onClick={() => removeField(field.id)}
                disabled={fields.length === 1}
              >
                <X className="h-4 w-4" />
              </Button>
            </div>
          ))}
        </div>

        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={addField}>
            {t('editor.addInput')}
          </Button>
          <Button
            size="sm"
            onClick={() => testMutation.mutate()}
            disabled={testMutation.isPending}
          >
            <Play className="mr-2 h-4 w-4" />
            {testMutation.isPending ? t('editor.running') : t('editor.execute')}
          </Button>
        </div>

        {result && (
          <div className="space-y-2">
            <Label>{t('editor.testResult', { count: result.matchedRuleCount })}</Label>
            {result.resultList.length === 0 ? (
              <p className="text-sm text-muted-foreground">{t('editor.noRulesMatched')}</p>
            ) : (
              result.resultList.map((entry, i) => (
                <pre
                  key={i}
                  className="bg-muted rounded p-3 text-xs overflow-auto"
                >
                  {JSON.stringify(entry, null, 2)}
                </pre>
              ))
            )}
          </div>
        )}
      </CardContent>
    </Card>
  )
}

function isNumeric(value: string): boolean {
  return value !== '' && !isNaN(Number(value))
}
