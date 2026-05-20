'use client'

import { useState } from 'react'
import { useFeelExpressions, useLocale, usePlatformCapabilities } from '@/lib/platform/usePlatformCapabilities'
import { formatCurrency } from '@/lib/locale/currency'
import { PssPill, SuggestList, SuggestGroup, SuggestItem, Note } from '@/components/design/panel-primitives'

/**
 * FEEL expression suggestion panel for the DMN designer.
 * Displays configVars (DOA thresholds) for cell autocomplete.
 * Shown alongside the DMN editor in the "Suggest" tab.
 * Styled to match the DMN Editor design spec.
 */
export function FeelExpressionSuggestions() {
  const { data: catalog, isError } = useFeelExpressions()
  const { data: capabilities } = usePlatformCapabilities()
  const { data: locale } = useLocale()
  const [copied, setCopied] = useState<string | null>(null)

  const copy = (expr: string) => {
    navigator.clipboard.writeText(expr).catch(() => {})
    setCopied(expr)
    setTimeout(() => setCopied(null), 1500)
  }

  if (isError || !capabilities) {
    return (
      <Note variant="muted">
        Designer capabilities unavailable — some options may be limited
      </Note>
    )
  }

  const noConfigVars = !catalog || catalog.configVars.monetary.length === 0

  const currencyCode = locale?.currencyCode ?? 'INR'

  // Build FEEL range expressions from configVars monetary levels
  // e.g. "< configVars.L1" with resolved "< ₹10K" as meta
  const buildMonetaryRangeItems = () => {
    if (!catalog) return []
    const levels = catalog.configVars.monetary
    if (levels.length === 0) return []

    const items: { expr: string; meta: string }[] = []

    // First item: < L1
    const l1 = levels[0]
    items.push({
      expr: `< configVars.${l1.key}`,
      meta: `< ${formatCurrency(Number(l1.value), locale)}`,
    })

    // Middle items: (L(n)..L(n+1)]
    for (let i = 0; i < levels.length - 1; i++) {
      const lo = levels[i]
      const hi = levels[i + 1]
      items.push({
        expr: `(configVars.${lo.key}..configVars.${hi.key}]`,
        meta: `${formatCurrency(Number(lo.value), locale)}–${formatCurrency(Number(hi.value), locale)}`,
      })
    }

    // Last item: > L(last)
    const last = levels[levels.length - 1]
    items.push({
      expr: `> configVars.${last.key}`,
      meta: `> ${formatCurrency(Number(last.value), locale)}`,
    })

    return items
  }

  const rangeItems = buildMonetaryRangeItems()

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
      {/* ── Cell autocomplete section ── */}
      <div>
        <p
          style={{
            fontSize: '10px',
            color: '#6b7e8c',
            textTransform: 'uppercase',
            letterSpacing: '0.06em',
            fontWeight: 700,
            marginBottom: '6px',
          }}
        >
          Cell autocomplete
        </p>

        {noConfigVars ? (
          <Note variant="muted">
            No approval thresholds configured — values will be literal only.
          </Note>
        ) : (
          <SuggestList>
            <SuggestGroup title={`configVars · ${currencyCode}`}>
              {rangeItems.map(({ expr, meta }) => (
                <SuggestItem
                  key={expr}
                  label={copied === expr ? 'Copied!' : expr}
                  meta={meta}
                  kind="feel"
                  onClick={() => copy(expr)}
                />
              ))}
            </SuggestGroup>
          </SuggestList>
        )}
        <PssPill endpoint={`/feel-expressions · ${currencyCode}`} />
      </div>

      {/* ── Informational note ── */}
      <Note variant="info">
        Suggestions are tenant-aware. SMB tenants get literal numbers/strings only — same surface, no broken paths (ADR-006)
      </Note>
    </div>
  )
}

export default FeelExpressionSuggestions
