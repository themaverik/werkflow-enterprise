'use client'

import { useState, useEffect, useCallback } from 'react'
import { useSession } from 'next-auth/react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { RefreshCw, RotateCcw, Trash2, AlertTriangle } from 'lucide-react'
import { listDeadLetterJobs, retryDeadLetterJob, deleteDeadLetterJob, type DeadLetterJob } from '@/lib/api/jobs'

function formatDate(iso: string | null): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleString()
}

function truncate(text: string | null, max: number): string {
  if (!text) return '—'
  return text.length > max ? text.slice(0, max) + '…' : text
}

export default function DeadLetterJobsPage() {
  const { status } = useSession()
  const [jobs, setJobs] = useState<DeadLetterJob[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionJobId, setActionJobId] = useState<string | null>(null)

  const load = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      const data = await listDeadLetterJobs()
      setJobs(data)
    } catch {
      setError('Failed to load failed jobs. Check engine connectivity.')
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    if (status === 'authenticated') load()
  }, [status, load])

  const handleRetry = async (jobId: string) => {
    setActionJobId(jobId)
    try {
      await retryDeadLetterJob(jobId)
      await load()
    } catch {
      setError(`Failed to retry job ${jobId}`)
    } finally {
      setActionJobId(null)
    }
  }

  const handleDelete = async (jobId: string) => {
    if (!confirm('Permanently delete this job? This cannot be undone.')) return
    setActionJobId(jobId)
    try {
      await deleteDeadLetterJob(jobId)
      setJobs(prev => prev.filter(j => j.jobId !== jobId))
    } catch {
      setError(`Failed to delete job ${jobId}`)
    } finally {
      setActionJobId(null)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Failed Jobs</h1>
          <p className="text-sm text-muted-foreground mt-1">
            Async jobs that exhausted all retry attempts. Retry to requeue or delete to remove permanently.
          </p>
        </div>
        <Button variant="outline" size="sm" onClick={load} disabled={isLoading}>
          <RefreshCw className={`mr-2 h-4 w-4 ${isLoading ? 'animate-spin' : ''}`} />
          Refresh
        </Button>
      </div>

      {error && (
        <div className="flex items-center gap-2 rounded-md border border-destructive/50 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          <AlertTriangle className="h-4 w-4 shrink-0" />
          {error}
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            Dead-Letter Queue
            {jobs.length > 0 && (
              <Badge variant="destructive">{jobs.length}</Badge>
            )}
          </CardTitle>
          <CardDescription>
            Jobs listed in ascending due-date order. Retry restores 3 attempts by default.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <p className="text-sm text-muted-foreground py-8 text-center">Loading…</p>
          ) : jobs.length === 0 ? (
            <p className="text-sm text-muted-foreground py-8 text-center">No failed jobs — queue is clear.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b text-left text-muted-foreground">
                    <th className="pb-2 pr-4 font-medium">Job ID</th>
                    <th className="pb-2 pr-4 font-medium">Process</th>
                    <th className="pb-2 pr-4 font-medium">Tenant</th>
                    <th className="pb-2 pr-4 font-medium">Error</th>
                    <th className="pb-2 pr-4 font-medium">Created</th>
                    <th className="pb-2 font-medium">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {jobs.map(job => (
                    <tr key={job.jobId} className="border-b last:border-0">
                      <td className="py-3 pr-4 font-mono text-xs">{job.jobId.slice(0, 8)}…</td>
                      <td className="py-3 pr-4">
                        <div className="font-medium">{job.processDefinitionKey ?? '—'}</div>
                        <div className="text-xs text-muted-foreground">{job.processInstanceId?.slice(0, 12) ?? '—'}…</div>
                      </td>
                      <td className="py-3 pr-4">
                        <Badge variant="outline">{job.tenantId ?? 'global'}</Badge>
                      </td>
                      <td className="py-3 pr-4 text-destructive max-w-xs">
                        {truncate(job.exceptionMessage, 80)}
                      </td>
                      <td className="py-3 pr-4 text-xs text-muted-foreground whitespace-nowrap">
                        {formatDate(job.createTime)}
                      </td>
                      <td className="py-3">
                        <div className="flex gap-2">
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleRetry(job.jobId)}
                            disabled={actionJobId === job.jobId}
                          >
                            <RotateCcw className="mr-1 h-3 w-3" />
                            Retry
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            className="text-destructive hover:text-destructive"
                            onClick={() => handleDelete(job.jobId)}
                            disabled={actionJobId === job.jobId}
                          >
                            <Trash2 className="h-3 w-3" />
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
