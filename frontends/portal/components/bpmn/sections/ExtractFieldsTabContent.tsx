'use client'

import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { X } from 'lucide-react'
import { useTranslations } from 'next-intl'

interface ExtractFieldsTabContentProps {
  extractFields: Array<{ variable: string; path: string }>
  onFieldChange: (index: number, field: 'variable' | 'path', value: string) => void
  onAddField: () => void
  onRemoveField: (index: number) => void
}

export default function ExtractFieldsTabContent({
  extractFields,
  onFieldChange,
  onAddField,
  onRemoveField,
}: ExtractFieldsTabContentProps) {
  const t = useTranslations('bpmn')

  return (
    <Card>
      <CardContent className="space-y-2 px-3 pb-3 pt-3">
        {extractFields.length === 0 && (
          <p className="text-xs text-muted-foreground">{t('noExtractFields')}</p>
        )}
        {extractFields.map((row, index) => (
          <div key={index} className="flex gap-1 items-center">
            <label htmlFor={`ef-var-${index}`} className="sr-only">Variable name {index + 1}</label>
            <Input
              id={`ef-var-${index}`}
              value={row.variable}
              onChange={e => onFieldChange(index, 'variable', e.target.value)}
              className="h-7 text-xs flex-1"
              placeholder="varName"
            />
            <label htmlFor={`ef-path-${index}`} className="sr-only">JSON path {index + 1}</label>
            <Input
              id={`ef-path-${index}`}
              value={row.path}
              onChange={e => onFieldChange(index, 'path', e.target.value)}
              className="h-7 text-xs flex-1 font-mono"
              placeholder="$.field"
            />
            <Button
              variant="ghost"
              size="sm"
              className="h-7 w-7 p-0 text-destructive shrink-0"
              aria-label={`Remove extract field row ${index + 1}${row.variable ? ` (${row.variable})` : ''}`}
              onClick={() => onRemoveField(index)}
            >
              <X className="h-3 w-3" />
            </Button>
          </div>
        ))}
        <Button
          variant="outline"
          size="sm"
          className="w-full h-7 text-xs"
          onClick={onAddField}
        >
          {t('addRow')}
        </Button>
      </CardContent>
    </Card>
  )
}
