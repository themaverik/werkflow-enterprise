'use client'

import { useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import { useMutation } from '@tanstack/react-query'
import { useTranslations } from 'next-intl'
import { ArrowLeft, Rocket } from 'lucide-react'
import Link from 'next/link'
import dynamic from 'next/dynamic'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useToast } from '@/hooks/use-toast'
import { deployDecision } from '@/lib/api/dmn'
import type { DmnEditorHandle } from '@/components/dmn/DmnEditor'
import DmnTestPanel from '@/components/dmn/DmnTestPanel'

// dmn-js requires a browser environment — load dynamically
const DmnEditor = dynamic(() => import('@/components/dmn/DmnEditor'), { ssr: false })

export default function NewDecisionPage() {
  const t = useTranslations('decisions')
  const router = useRouter()
  const { toast } = useToast()
  const editorRef = useRef<DmnEditorHandle>(null)
  const [name, setName] = useState('')
  const [showTestPanel, setShowTestPanel] = useState(false)
  const [deployedKey, setDeployedKey] = useState<string | null>(null)

  const deployMutation = useMutation({
    mutationFn: async () => {
      if (!name.trim()) throw new Error(t('editor.nameRequired'))
      const xml = await editorRef.current?.getXml()
      if (!xml) throw new Error(t('editor.xmlRequired'))
      return deployDecision(name.trim(), xml)
    },
    onSuccess: (result) => {
      toast({ title: t('editor.deploySuccess'), description: result.key })
      router.push('/decisions')
    },
    onError: (err: Error) => {
      toast({ title: t('editor.deployFailed'), description: err.message, variant: 'destructive' })
    },
  })

  return (
    <div className="container mx-auto py-6 space-y-4">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="sm" asChild>
          <Link href="/decisions">
            <ArrowLeft className="mr-1 h-4 w-4" />
            {t('editor.back')}
          </Link>
        </Button>
        <h1 className="text-2xl font-bold">{t('editor.newTitle')}</h1>
      </div>

      <div className="flex items-end gap-4">
        <div className="flex-1 max-w-xs space-y-1">
          <Label htmlFor="decisionName">{t('editor.nameLabel')}</Label>
          <Input
            id="decisionName"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder={t('editor.namePlaceholder')}
          />
        </div>
        <Button
          variant="outline"
          onClick={() => setShowTestPanel((p) => !p)}
          disabled={!deployedKey}
        >
          {t('editor.test')}
        </Button>
        <Button
          onClick={() => deployMutation.mutate()}
          disabled={deployMutation.isPending || !name.trim()}
        >
          <Rocket className="mr-2 h-4 w-4" />
          {deployMutation.isPending ? t('editor.deploying') : t('editor.deploy')}
        </Button>
      </div>

      <DmnEditor ref={editorRef} />

      {showTestPanel && deployedKey && (
        <DmnTestPanel decisionKey={deployedKey} />
      )}
    </div>
  )
}
