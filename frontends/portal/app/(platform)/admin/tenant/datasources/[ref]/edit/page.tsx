'use client'

import { useParams } from 'next/navigation'
import { useSession } from 'next-auth/react'
import { useQuery } from '@tanstack/react-query'
import { RefreshCw } from 'lucide-react'
import { DatasourceForm } from '../../_components/DatasourceForm'
import { getDatasource } from '@/lib/api/datasources'

export default function EditDatasourcePage() {
  const params = useParams<{ ref: string }>()
  const ref = params.ref ?? ''
  const { data: session, status } = useSession()
  const token = (session?.accessToken as string) ?? ''

  const { data: datasource, isLoading, error } = useQuery({
    queryKey: ['tenant-datasource', ref],
    queryFn: () => getDatasource(ref, token),
    enabled: status === 'authenticated',
  })

  if (isLoading) {
    return (
      <div className="flex items-center gap-2 py-12 text-muted-foreground">
        <RefreshCw className="h-5 w-5 animate-spin" />
        Loading datasource…
      </div>
    )
  }

  if (error || !datasource) {
    return (
      <div className="py-12 text-destructive text-sm">
        Failed to load datasource{' '}
        <code className="font-mono bg-muted px-1 rounded">{ref}</code>.
      </div>
    )
  }

  return (
    <div className="max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Edit Datasource</h1>
        <p className="text-sm text-muted-foreground mt-0.5">
          Update the configuration for{' '}
          <code className="font-mono text-xs bg-muted px-1 rounded">{ref}</code>.
        </p>
      </div>
      <DatasourceForm existing={datasource} />
    </div>
  )
}
