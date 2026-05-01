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
import { Input } from '@/components/ui/input'
import { FilterPills } from '@/components/ui/filter-pills'
import { StatusBadge } from '@/components/ui/status-badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Card, CardContent } from '@/components/ui/card'
import { getAllWorkflowInstances, type WorkflowInstance } from '@/lib/api/workflows'
import { formatDate } from '@/lib/utils/format'
import { useAuth } from '@/lib/auth/auth-context'
import { Button } from '@/components/ui/button'

type StatusFilter = 'all' | 'active' | 'completed' | 'suspended'

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
  const [activeTab, setActiveTab] = useState<string>('all')
  const statusFilter = activeTab as StatusFilter
  const [search, setSearch] = useState('')

  const queryStatus = statusFilter === 'all' ? undefined : statusFilter
  const currentUsername = user?.username || ''

  const { data: instances, isLoading } = useQuery({
    queryKey: ['workflow-instances', activeTab, currentUsername],
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
        <FilterPills
          options={[
            { key: 'all',       label: 'All' },
            { key: 'active',    label: 'Active' },
            { key: 'completed', label: 'Completed' },
            { key: 'suspended', label: 'Suspended' },
          ]}
          active={activeTab}
          onChange={setActiveTab}
        />

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
        <div className="bg-card border border-border rounded-xl overflow-hidden">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">{t('processName')}</TableHead>
                <TableHead className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">{t('businessKey')}</TableHead>
                <TableHead className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">{t('status')}</TableHead>
                <TableHead className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">{t('started')}</TableHead>
                <TableHead className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">{t('currentActivity')}</TableHead>
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
