'use client'

import { useState } from 'react'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { Badge } from '@/components/ui/badge'
import {
  ConnectorTestResponse,
  testConnector,
} from '@/lib/api/connectors'
import { useTranslations } from 'next-intl'

interface ContractTabContentProps {
  selectedConnectorKey: string
  initialContractJson: string
  /** Returns true if the JSON was valid and applied; false if validation failed. */
  onApplyContract: (jsonSource: string) => boolean
}

export default function ContractTabContent({
  selectedConnectorKey,
  initialContractJson,
  onApplyContract,
}: ContractTabContentProps) {
  const t = useTranslations('bpmn')

  const [contractMode, setContractMode] = useState<'import' | 'test'>('import')
  const [contractJson, setContractJson] = useState(initialContractJson)
  const [contractTestPath, setContractTestPath] = useState('')
  const [contractTestMethod, setContractTestMethod] = useState<
    'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  >('GET')
  const [contractTestResult, setContractTestResult] = useState<ConnectorTestResponse | null>(null)
  const [contractTestLoading, setContractTestLoading] = useState(false)
  const [contractTestError, setContractTestError] = useState<string | null>(null)
  const [contractImportError, setContractImportError] = useState<string | null>(null)

  const handleTestCall = async () => {
    if (!selectedConnectorKey || !contractTestPath) return
    setContractTestLoading(true)
    setContractTestError(null)
    setContractTestResult(null)
    try {
      const result = await testConnector(selectedConnectorKey, {
        path: contractTestPath,
        method: contractTestMethod,
      })
      setContractTestResult(result)
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Test call failed'
      setContractTestError(message)
    } finally {
      setContractTestLoading(false)
    }
  }

  const statusBadgeVariant = (
    code: number
  ): 'default' | 'secondary' | 'destructive' | 'outline' => {
    if (code >= 200 && code < 300) return 'default'
    if (code >= 400) return 'destructive'
    return 'secondary'
  }

  return (
    <Card>
      <CardContent className="space-y-3 px-3 pb-3 pt-3">
        {/* Mode toggle */}
        <div className="flex gap-2">
          <Button
            size="sm"
            variant={contractMode === 'import' ? 'default' : 'outline'}
            className="flex-1 h-7 text-xs"
            onClick={() => setContractMode('import')}
          >
            {t('pasteJson')}
          </Button>
          <Button
            size="sm"
            variant={contractMode === 'test' ? 'default' : 'outline'}
            className="flex-1 h-7 text-xs"
            onClick={() => setContractMode('test')}
          >
            {t('testCall')}
          </Button>
        </div>

        {/* Import mode */}
        {contractMode === 'import' && (
          <div className="space-y-2">
            <Label className="text-xs">{t('sampleResponseJson')}</Label>
            <Textarea
              value={contractJson}
              onChange={e => setContractJson(e.target.value)}
              className="text-xs font-mono min-h-[120px] resize-y"
              placeholder={'{\n  "id": 123,\n  "status": "approved"\n}'}
            />
            <Button
              size="sm"
              className="w-full h-7 text-xs"
              onClick={() => {
                const ok = onApplyContract(contractJson)
                if (!ok) {
                  setContractImportError('Invalid JSON or not a plain object. Please paste a valid JSON object.')
                } else {
                  setContractImportError(null)
                }
              }}
              disabled={!contractJson.trim()}
            >
              {t('applyContract')}
            </Button>
            {contractImportError && (
              <p className="text-xs text-destructive">{contractImportError}</p>
            )}
          </div>
        )}

        {/* Test Call mode */}
        {contractMode === 'test' && (
          <div className="space-y-2">
            {!selectedConnectorKey && (
              <p className="text-xs text-muted-foreground">{t('selectConnectorForTest')}</p>
            )}
            <div className="flex gap-2">
              <Select
                value={contractTestMethod}
                onValueChange={v =>
                  setContractTestMethod(
                    v as 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
                  )
                }
              >
                <SelectTrigger className="h-8 text-xs w-24 shrink-0">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {(['GET', 'POST', 'PUT', 'PATCH', 'DELETE'] as const).map(m => (
                    <SelectItem key={m} value={m}>
                      {m}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Input
                value={contractTestPath}
                onChange={e => setContractTestPath(e.target.value)}
                className="h-8 text-xs flex-1"
                placeholder="/api/resource"
              />
            </div>
            <Button
              size="sm"
              className="w-full h-7 text-xs"
              onClick={handleTestCall}
              disabled={
                contractTestLoading || !selectedConnectorKey || !contractTestPath.trim()
              }
            >
              {contractTestLoading ? t('sending') : t('send')}
            </Button>

            {contractTestError && (
              <p className="text-xs text-destructive">{contractTestError}</p>
            )}

            {contractTestResult && (
              <div className="space-y-2">
                <div className="flex items-center gap-2">
                  <Badge variant={statusBadgeVariant(contractTestResult.statusCode)}>
                    {contractTestResult.statusCode}
                  </Badge>
                  <span className="text-xs text-muted-foreground">
                    {contractTestResult.durationMs}ms
                  </span>
                  {contractTestResult.truncated && (
                    <span className="text-xs text-amber-600">{t('truncated')}</span>
                  )}
                </div>
                <Textarea
                  readOnly
                  value={contractTestResult.body}
                  className="text-xs font-mono min-h-[100px] resize-y"
                />
                <Button
                  size="sm"
                  className="w-full h-7 text-xs"
                  onClick={() => onApplyContract(contractTestResult.body)}
                  disabled={!contractTestResult.body}
                >
                  {t('applyContract')}
                </Button>
              </div>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
