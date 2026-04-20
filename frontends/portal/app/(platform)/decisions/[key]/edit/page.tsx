'use client'

import { useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslations } from 'next-intl'
import { ArrowLeft, Rocket, FlaskConical } from 'lucide-react'
import Link from 'next/link'
import dynamic from 'next/dynamic'
import { Button } from '@/components/ui/button'
import { ErrorDisplay, LoadingState } from '@/components/ui/error-display'
import { useToast } from '@/hooks/use-toast'
import { getDecisionXml, getDecision, redeployDecision } from '@/lib/api/dmn'
import type { DmnEditorHandle } from '@/components/dmn/DmnEditor'
import DmnTestPanel from '@/components/dmn/DmnTestPanel'

const DmnEditor = dynamic(() => import('@/components/dmn/DmnEditor'), { ssr: false })

interface EditDecisionPageProps {
  params: { key: string }
}

export default function EditDecisionPage({ params }: EditDecisionPageProps) {
  const { key } = params
  const t = useTranslations('decisions')
  const router = useRouter()
  const queryClient = useQueryClient()
  const { toast } = useToast()
  const editorRef = useRef<DmnEditorHandle>(null)
  const [showTestPanel, setShowTestPanel] = useState(false)

  const { data: meta, isLoading: metaLoading, error: metaError } = useQuery({
    queryKey: ['dmnDecision', key],
    queryFn: () => getDecision(key),
  })

  const { data: xml, isLoading: xmlLoading, error: xmlError } = useQuery({
    queryKey: ['dmnDecisionXml', key],
    queryFn: () => getDecisionXml(key),
    enabled: !!meta,
  })

  const redeployMutation = useMutation({
    mutationFn: async () => {
      const updatedXml = await editorRef.current?.getXml()
      if (!updatedXml) throw new Error(t('editor.xmlRequired'))
      return redeployDecision(key, meta?.name ?? key, updatedXml)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dmnDecisions'] })
      queryClient.invalidateQueries({ queryKey: ['dmnDecisionXml', key] })
      toast({ title: t('editor.redeploySuccess') })
      router.push('/decisions')
    },
    onError: (err: Error) => {
      toast({ title: t('editor.deployFailed'), description: err.message, variant: 'destructive' })
    },
  })

  if (metaLoading || xmlLoading) return <LoadingState />
  if (metaError || xmlError) return <ErrorDisplay error={(metaError ?? xmlError) as Error} />

  return (
    <div className="container mx-auto py-6 space-y-4">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="sm" asChild>
          <Link href="/decisions">
            <ArrowLeft className="mr-1 h-4 w-4" />
            {t('editor.back')}
          </Link>
        </Button>
        <div>
          <h1 className="text-2xl font-bold">{meta?.name}</h1>
          <p className="font-mono text-sm text-muted-foreground">
            {key} &mdash; v{meta?.version}
          </p>
        </div>
      </div>

      <div className="flex justify-end gap-2">
        <Button variant="outline" onClick={() => setShowTestPanel((p) => !p)}>
          <FlaskConical className="mr-2 h-4 w-4" />
          {t('editor.test')}
        </Button>
        <Button
          onClick={() => redeployMutation.mutate()}
          disabled={redeployMutation.isPending}
        >
          <Rocket className="mr-2 h-4 w-4" />
          {redeployMutation.isPending ? t('editor.deploying') : t('editor.redeploy')}
        </Button>
      </div>

      <DmnEditor ref={editorRef} xml={xml} />

      {showTestPanel && <DmnTestPanel decisionKey={key} />}
    </div>
  )
}
