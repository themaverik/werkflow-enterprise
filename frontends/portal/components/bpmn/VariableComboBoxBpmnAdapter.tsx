'use client'
import React, { useState, useCallback, useEffect } from 'react'
import { VariableComboBox } from '@/components/ui/VariableComboBox'
import type { GroupItem, Chip, Source } from '@/components/ui/VariableComboBox'
import { useVariableSources } from '@/lib/bpmn/useVariableSources'
import { useComboboxKeyboard } from '@/lib/bpmn/useComboboxKeyboard'
import { filterGroups } from '@/lib/bpmn/filterGroups'

// ── Props ─────────────────────────────────────────────────────────────────────

export interface VariableComboBoxBpmnAdapterProps {
  mode: 'multi' | 'single'
  getValue: () => string
  setValue: (v: string) => void
  sourceKeys: string[]
  processId?: string
  activityId?: string
  placeholder?: string
  keys?: boolean
  label?: string
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function parseChips(raw: string): Chip[] {
  if (!raw) return []
  return raw
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
    .map((v) => ({ id: v, label: v }))
}

function buildSources(sourceKeys: string[]): Source[] {
  return sourceKeys.reduce<Source[]>((acc, key) => {
    if (key.includes('pss')) {
      acc.push({ kind: 'pss', path: key })
    } else if (key.includes('dtds')) {
      acc.push({ kind: 'dtds', path: key })
    }
    return acc
  }, [])
}

// ── Component ─────────────────────────────────────────────────────────────────

/**
 * React adapter that owns all interactive state for the VariableComboBox
 * when rendered inside a bpmn-js properties panel slot.
 *
 * This component is mounted imperatively via `createRoot` inside
 * `VariableComboBoxEntry` (a Preact class component), which isolates
 * React hooks from bpmn-js's bundled Preact copy.
 */
export function VariableComboBoxBpmnAdapter({
  mode,
  getValue,
  setValue,
  sourceKeys,
  processId,
  activityId,
  placeholder = 'Type or pick…',
  keys = false,
  label,
}: VariableComboBoxBpmnAdapterProps) {
  const [query, setQuery] = useState('')
  const [chips, setChips] = useState<Chip[]>(() => parseChips(getValue()))

  // Re-sync chips when the external value changes (e.g. undo/redo in bpmn-js)
  useEffect(() => {
    const external = getValue()
    const current = chips.map((c) => c.id).join(',')
    if (external !== current) {
      setChips(parseChips(external))
    }
    // chips intentionally omitted — we only want to react to external changes
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [getValue])

  const { groups, loading } = useVariableSources(sourceKeys, { processId, activityId })
  const filteredGroups = filterGroups(groups, query)

  const allEmpty = filteredGroups.every((g) => g.items.length === 0)
  const literalEscape = React.useMemo(() => {
    if (!allEmpty || loading) return null
    if (query.length > 0 && !query.includes(',')) {
      // existing behaviour: show literal escape for any non-empty query when all groups empty
      return { value: query, hint: `No matches — press Enter to use "${query}" as a literal value` }
    }
    if (mode === 'single') {
      // eager literal escape for single-mode when no data available at all
      return { value: '', hint: 'No options found — type a value and press Enter' }
    }
    return null
  }, [allEmpty, loading, query, mode])

  const onCommit = useCallback(
    (item: GroupItem) => {
      const newChips: Chip[] =
        mode === 'single'
          ? [{ id: item.id, label: item.name }]
          : chips.some((c) => c.id === item.id)
            ? chips
            : [...chips, { id: item.id, label: item.name }]
      setChips(newChips)
      setValue(newChips.map((c) => c.id).join(','))
      setQuery('')
    },
    [chips, mode, setValue]
  )

  const onRemoveLast = useCallback(() => {
    if (chips.length === 0) return
    const next = chips.slice(0, -1)
    setChips(next)
    setValue(next.map((c) => c.id).join(','))
  }, [chips, setValue])

  const onRemoveChip = useCallback(
    (chipId: string) => {
      const next = chips.filter((c) => c.id !== chipId)
      setChips(next)
      setValue(next.map((c) => c.id).join(','))
    },
    [chips, setValue]
  )

  const onClose = useCallback(() => {
    setQuery('')
  }, [])

  const commitLiteral = useCallback(() => {
    if (!query.trim() || query.includes(',')) return
    const val = query.trim()
    const newChip: Chip = { id: val, label: val, warn: true }
    const newChips = mode === 'single' ? [newChip] : [...chips, newChip]
    setChips(newChips)
    setValue(newChips.map((c) => c.id).join(','))
    setQuery('')
  }, [query, chips, mode, setValue])

  const { focusedId, handlers: keyboardHandlers } = useComboboxKeyboard({
    groups: filteredGroups,
    onCommit,
    onRemoveLast,
    onClose,
    query,
  })

  // Intercept Enter for literal escape — useComboboxKeyboard only commits
  // when focusedId matches an item; we wrap to also handle literal commit.
  const handleKeyDown = useCallback<React.KeyboardEventHandler>(
    (e) => {
      if (e.key === 'Enter' && literalEscape) {
        e.preventDefault()
        commitLiteral()
        return
      }
      keyboardHandlers(e)
    },
    [keyboardHandlers, literalEscape, commitLiteral]
  )

  const sources = buildSources(sourceKeys)

  return (
    <div onKeyDown={handleKeyDown}>
      {label && (
        <label className="bio-properties-panel-label">{label}</label>
      )}
      <VariableComboBox
        mode={mode}
        placeholder={placeholder}
        query={query}
        onQuery={setQuery}
        chips={chips}
        onRemoveChip={onRemoveChip}
        groups={filteredGroups}
        focusedId={focusedId}
        onItemClick={onCommit}
        literalEscape={literalEscape}
        sources={sources}
        keys={keys}
        loading={loading}
      />
    </div>
  )
}

export default VariableComboBoxBpmnAdapter
