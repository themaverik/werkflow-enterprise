'use client'

import { useState } from 'react'
import { useFeelExpressions, usePlatformCapabilities } from '@/lib/platform/usePlatformCapabilities'
import { Badge } from '@/components/ui/badge'

/**
 * FEEL expression suggestion panel for the DMN designer.
 * Displays available configVars (DOA thresholds) and custodyVars expressions
 * sourced from the PSS. Users can click to copy an expression.
 *
 * Shown as a collapsible sidebar alongside the DMN editor.
 */
export function FeelExpressionSuggestions() {
  const { data: catalog, isError } = useFeelExpressions()
  const { data: capabilities } = usePlatformCapabilities()
  const [copied, setCopied] = useState<string | null>(null)

  const copy = (expr: string) => {
    navigator.clipboard.writeText(expr).catch(() => {})
    setCopied(expr)
    setTimeout(() => setCopied(null), 1500)
  }

  if (isError || !capabilities) {
    return (
      <div className="text-xs text-muted-foreground p-2 border rounded">
        Designer capabilities unavailable — some options may be limited
      </div>
    )
  }

  const noConfigVars = !catalog || catalog.configVars.monetary.length === 0
  const noCustodyVars = !catalog || catalog.custodyVars.groupResolutions.length === 0

  return (
    <div className="space-y-3 text-xs">
      <p className="font-semibold uppercase tracking-wide text-muted-foreground">
        FEEL Expressions
      </p>

      {noConfigVars ? (
        <p className="text-muted-foreground italic">
          No approval thresholds configured — values will be literal only.
        </p>
      ) : (
        <div className="space-y-1">
          <p className="font-medium text-muted-foreground">Approval Thresholds</p>
          {catalog!.configVars.monetary.map((entry) => (
            <div key={entry.key} className="space-y-0.5">
              <p className="text-muted-foreground font-medium">
                {entry.label} ({entry.currency} {entry.value})
              </p>
              {entry.feelExpressions.map((expr) => (
                <button
                  key={expr}
                  type="button"
                  onClick={() => copy(expr)}
                  className="block w-full text-left font-mono bg-muted px-2 py-0.5 rounded hover:bg-primary/10 transition-colors"
                >
                  {copied === expr ? (
                    <span className="text-green-600">Copied!</span>
                  ) : expr}
                </button>
              ))}
            </div>
          ))}
        </div>
      )}

      {!noCustodyVars && (
        <div className="space-y-1">
          <p className="font-medium text-muted-foreground">Custody Groups</p>
          {catalog!.custodyVars.groupResolutions.map((res) => (
            <div key={res.key} className="space-y-0.5">
              <p className="text-muted-foreground">{res.key}</p>
              <button
                type="button"
                onClick={() => copy(res.feelExpression)}
                className="block w-full text-left font-mono bg-muted px-2 py-0.5 rounded hover:bg-primary/10 transition-colors"
              >
                {copied === res.feelExpression ? (
                  <span className="text-green-600">Copied!</span>
                ) : res.feelExpression}
              </button>
              <div className="flex flex-wrap gap-1 mt-0.5">
                {res.candidateGroups.map((g) => (
                  <Badge key={g} variant="outline" className="text-xs">{g}</Badge>
                ))}
              </div>
            </div>
          ))}
          {catalog!.custodyVars.lookupExpressions.map((expr) => (
            <button
              key={expr}
              type="button"
              onClick={() => copy(expr)}
              className="block w-full text-left font-mono bg-muted px-2 py-0.5 rounded hover:bg-primary/10 transition-colors"
            >
              {copied === expr ? <span className="text-green-600">Copied!</span> : expr}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

export default FeelExpressionSuggestions
