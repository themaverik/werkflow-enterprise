'use client'

import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import Link from 'next/link'
import { useTranslations } from 'next-intl'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Skeleton } from '@/components/ui/skeleton'
import { Card, CardContent } from '@/components/ui/card'
import { getAllWorkflowInstances, type WorkflowInstance } from '@/lib/api/workflows'
import { formatDate } from '@/lib/utils/format'
import { useAuth } from '@/lib/auth/auth-context'
import { Button } from '@/components/ui/button'

type StatusFilter = 'all' | 'active' | 'completed' | 'suspended'

function StatusBadge({ status }: { status: WorkflowInstance['status'] }) {
  const t = useTranslations('requests')
  switch (status) {
    case 'active':
      return <Badge className="bg-blue-100 text-blue-800 border-blue-200 hover:bg-blue-100">{t('active')}</Badge>
    case 'completed':
      return <Badge className="bg-green-100 text-green-800 border-green-200 hover:bg-green-100">{t('completed')}</Badge>
    case 'failed':
      return <Badge className="bg-red-100 text-red-800 border-red-200 hover:bg-red-100">{t('failed')}</Badge>
    case 'suspended':
      return <Badge className="bg-yellow-100 text-yellow-800 border-yellow-200 hover:bg-yellow-100">{t('suspended')}</Badge>
    default:
      return <Badge variant="outline">{status}</Badge>
  }
}

function TableSkeleton() {
  return (
    <div className="space-y-2">
      {Array.from({ length: 5 }).map((_, i) => (
        <Skeleton key={i} className="h-12 w-full rounded" />
      ))}
    </div>
  )
}

export default function RequestsPage() {
  const t = useTranslations('requests')
  const { user } = useAuth()
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all')
  const [search, setSearch] = useState('')

  const queryStatus = statusFilter === 'all' ? undefined : statusFilter
  const currentUsername = user?.username || ''

  const { data: instances, isLoading } = useQuery({
    queryKey: ['workflow-instances', statusFilter, currentUsername],
    queryFn: () => getAllWorkflowInstances(queryStatus, 100, currentUsername),
    enabled: !!currentUsername,
  })

  const filtered = (instances ?? []).filter((instance) => {
    if (!search) return true
    const term = search.toLowerCase()
    const key = (instance.businessKey ?? '').toLowerCase()
    const name = (instance.processDefinitionName ?? instance.processDefinitionKey ?? '').toLowerCase()
    return key.includes(term) || name.includes(term) || instance.id.toLowerCase().includes(term)
  })

  return (
    <div className="container py-6">
      <div className="mb-6 flex items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold">{t('title')}</h1>
          <p className="text-muted-foreground">{t('subtitle')}</p>
        </div>
        <Button asChild>
          <Link href="/processes">{t('startRequest')}</Link>
        </Button>
      </div>

      <div className="flex flex-col sm:flex-row sm:items-center gap-4 mb-6">
        <Tabs
          value={statusFilter}
          onValueChange={(v) => setStatusFilter(v as StatusFilter)}
        >
          <TabsList>
            {(['all', 'active', 'completed', 'suspended'] as StatusFilter[]).map((value) => (
              <TabsTrigger key={value} value={value}>
                {t(value as 'all' | 'active' | 'completed' | 'suspended')}
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>

        <Input
          placeholder={t('searchPlaceholder')}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="sm:max-w-xs"
        />
      </div>

      {isLoading ? (
        <TableSkeleton />
      ) : filtered.length === 0 ? (
        <Card>
          <CardContent className="py-16 text-center">
            <p className="text-muted-foreground">{t('noRequestsFound')}</p>
          </CardContent>
        </Card>
      ) : (
        <div className="rounded-md border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('processName')}</TableHead>
                <TableHead>{t('businessKey')}</TableHead>
                <TableHead>{t('status')}</TableHead>
                <TableHead>{t('started')}</TableHead>
                <TableHead>{t('currentActivity')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.map((instance) => (
                <TableRow key={instance.id}>
                  <TableCell className="font-medium">
                    {instance.processDefinitionName || instance.processDefinitionKey}
                  </TableCell>
                  <TableCell>
                    {instance.businessKey ? (
                      <Link
                        href={`/requests/${instance.id}`}
                        className="text-primary underline-offset-4 hover:underline font-mono text-sm"
                      >
                        {instance.businessKey}
                      </Link>
                    ) : (
                      <Link
                        href={`/requests/${instance.id}`}
                        className="text-muted-foreground underline-offset-4 hover:underline font-mono text-sm"
                      >
                        {instance.id.substring(0, 8)}...
                      </Link>
                    )}
                  </TableCell>
                  <TableCell><StatusBadge status={instance.status} /></TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    {formatDate(instance.startTime)}
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    {instance.currentActivity ?? '-'}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  )
}
