'use client'

import { AlertCircle, RefreshCw, WifiOff } from 'lucide-react'
import { Button } from './button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from './card'
import { useTranslations } from 'next-intl'

interface ErrorDisplayProps {
  error: Error
  onRetry?: () => void
  title?: string
  className?: string
}

export function ErrorDisplay({ error, onRetry, title, className }: ErrorDisplayProps) {
  const t = useTranslations('common.errorDisplay')
  const isNetworkError = error.name === 'NetworkError'
  const statusCode = (error as any).statusCode

  return (
    <Card className={className}>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-destructive">
          {isNetworkError ? (
            <WifiOff className="h-5 w-5" />
          ) : (
            <AlertCircle className="h-5 w-5" />
          )}
          {title || (isNetworkError ? t('connectionError') : t('error'))}
        </CardTitle>
        <CardDescription>
          {statusCode && `Error ${statusCode}`}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <div className="space-y-4">
          <p className="text-sm text-muted-foreground">
            {error.message || t('unexpectedError')}
          </p>

          {isNetworkError && (
            <div className="rounded-md bg-muted p-4">
              <p className="text-sm font-medium mb-2">{t('troubleshooting')}</p>
              <ul className="text-sm text-muted-foreground space-y-1 list-disc list-inside">
                <li>{t('troubleshootBackend')}</li>
                <li>{t('troubleshootNetwork')}</li>
                <li>{t('troubleshootApiUrl')}</li>
                <li>{t('troubleshootCors')}</li>
              </ul>
            </div>
          )}

          {onRetry && (
            <Button onClick={onRetry} variant="outline" className="w-full">
              <RefreshCw className="h-4 w-4 mr-2" />
              {t('retry')}
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  )
}

interface LoadingStateProps {
  message?: string
  className?: string
}

export function LoadingState({ message = 'Loading...', className }: LoadingStateProps) {
  return (
    <Card className={className}>
      <CardContent className="py-12 text-center">
        <div className="flex flex-col items-center gap-4">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
          <p className="text-muted-foreground">{message}</p>
        </div>
      </CardContent>
    </Card>
  )
}

interface EmptyStateProps {
  icon?: React.ReactNode
  title: string
  description: string
  action?: React.ReactNode
  className?: string
}

export function EmptyState({ icon, title, description, action, className }: EmptyStateProps) {
  return (
    <Card className={className}>
      <CardContent className="py-12 text-center">
        {icon && <div className="mx-auto mb-4">{icon}</div>}
        <h3 className="text-lg font-semibold mb-2">{title}</h3>
        <p className="text-muted-foreground mb-4">{description}</p>
        {action}
      </CardContent>
    </Card>
  )
}
