'use client'

import { useTranslations } from 'next-intl'

interface ComingSoonPageProps {
  title: string
  description?: string
}

export function ComingSoonPage({ title, description }: ComingSoonPageProps) {
  const t = useTranslations('common')
  return (
    <div className="flex flex-col items-center justify-center min-h-[400px] text-center px-4">
      <div className="max-w-sm">
        <div className="inline-flex items-center rounded-full border px-3 py-1 text-xs font-medium text-muted-foreground mb-6">
          {t('comingSoon')}
        </div>
        <h1 className="text-2xl font-semibold text-foreground mb-3">{title}</h1>
        {description && (
          <p className="text-sm text-muted-foreground">{description}</p>
        )}
      </div>
    </div>
  )
}
