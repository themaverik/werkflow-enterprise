'use client'

import { useState } from 'react'
import Link from 'next/link'
import { useQuery } from '@tanstack/react-query'
import { useTranslations } from 'next-intl'
import { ArrowLeft } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { ErrorDisplay, LoadingState } from '@/components/ui/error-display'
import { getExecutionHistory, getDecision } from '@/lib/api/dmn'

interface ExecutionHistoryPageProps {
  params: { key: string }
}

export default function ExecutionHistoryPage({ params }: ExecutionHistoryPageProps) {
  const { key } = params
  const t = useTranslations('decisions')
  const [page, setPage] = useState(0)

  const { data: meta } = useQuery({
    queryKey: ['dmnDecision', key],
    queryFn: () => getDecision(key),
  })

  const { data: history, isLoading, error, refetch } = useQuery({
    queryKey: ['dmnExecutions', key, page],
    queryFn: () => getExecutionHistory(key, page, 20),
  })

  if (isLoading) return <LoadingState />
  if (error) return <ErrorDisplay error={error} onRetry={refetch} />

  const executions = history?.content ?? []

  return (
    <div className="container mx-auto py-6 space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="sm" asChild>
          <Link href="/decisions">
            <ArrowLeft className="mr-1 h-4 w-4" />
            {t('editor.back')}
          </Link>
        </Button>
        <div>
          <h1 className="text-2xl font-bold">{t('executions.title')}</h1>
          {meta && (
            <p className="font-mono text-sm text-muted-foreground">{meta.name} ({key})</p>
          )}
        </div>
      </div>

      {executions.length === 0 ? (
        <Card>
          <CardContent className="py-12 text-center text-muted-foreground">
            {t('executions.empty')}
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-4">
          {executions.map((exec) => (
            <Card key={exec.id}>
              <CardHeader className="pb-2">
                <CardTitle className="text-sm font-medium">
                  {new Date(exec.executedAt).toLocaleString()}
                  {exec.processInstanceId && (
                    <span className="ml-3 font-normal text-muted-foreground font-mono text-xs">
                      {t('executions.processInstance')}: {exec.processInstanceId}
                    </span>
                  )}
                </CardTitle>
              </CardHeader>
              <CardContent className="grid gap-4 sm:grid-cols-2 text-sm">
                <div>
                  <p className="font-medium mb-1">{t('executions.inputs')}</p>
                  <pre className="bg-muted rounded p-2 text-xs overflow-auto max-h-32">
                    {JSON.stringify(exec.inputs, null, 2)}
                  </pre>
                </div>
                <div>
                  <p className="font-medium mb-1">{t('executions.outputs')}</p>
                  <pre className="bg-muted rounded p-2 text-xs overflow-auto max-h-32">
                    {JSON.stringify(exec.outputs, null, 2)}
                  </pre>
                </div>
              </CardContent>
            </Card>
          ))}

          <div className="flex justify-between items-center pt-2">
            <Button
              variant="outline"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage((p) => p - 1)}
            >
              {t('executions.prev')}
            </Button>
            <span className="text-sm text-muted-foreground">
              {t('executions.pageInfo', { page: page + 1, total: history?.totalPages ?? 1 })}
            </span>
            <Button
              variant="outline"
              size="sm"
              disabled={page >= (history?.totalPages ?? 1) - 1}
              onClick={() => setPage((p) => p + 1)}
            >
              {t('executions.next')}
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
