import { h, Fragment } from 'preact'
import { useState, useEffect, useRef, useCallback } from 'preact/compat'
import { useVariableSources } from '@/lib/bpmn/useVariableSources'
import { filterGroups } from '@/lib/bpmn/filterGroups'
import type { Group, GroupItem, Chip, GroupKind } from '@/components/ui/VariableComboBox'

// ── Inline SVG icons (same paths as VariableComboBox) ────────────────────────

const ICON_PROPS = { width: 11, height: 11, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': 2 }

function GroupIcon({ kind }: { kind: GroupKind }) {
  switch (kind) {
    case 'process':   return h('svg', ICON_PROPS, h('path', { d: 'M4 7v-2h16v2M9 20h6M12 4v16' }))
    case 'custody':   return h('svg', ICON_PROPS, h('rect', { x: 3, y: 11, width: 18, height: 11, rx: 2 }), h('path', { d: 'M7 11V7a5 5 0 0 1 10 0v4' }))
    case 'business':  return h('svg', ICON_PROPS, h('path', { d: 'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2' }), h('circle', { cx: 9, cy: 7, r: 4 }))
    case 'system':    return h('svg', ICON_PROPS, h('path', { d: 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z' }))
    case 'feel':      return h('svg', ICON_PROPS, h('path', { d: 'M3 3l18 18M3 21L21 3' }))
    case 'literal':   return h('svg', ICON_PROPS, h('path', { d: 'M3 21l4-4 5 5 9-9M14 4h6v6' }))
    default:          return null
  }
}

// ── Substring highlight ───────────────────────────────────────────────────────

function highlight(name: string, q: string): preact.ComponentChild {
  if (!q) return name
  const idx = name.toLowerCase().indexOf(q.toLowerCase())
  if (idx === -1) return name
  return h(Fragment, null,
    name.slice(0, idx),
    h('span', { class: 'hl' }, name.slice(idx, idx + q.length)),
    name.slice(idx + q.length)
  )
}

// ── Props ─────────────────────────────────────────────────────────────────────

interface Props {
  id: string
  mode: 'multi' | 'single'
  getValue: () => string
  setValue: (v: string) => void
  sourceKeys: string[]
  processId?: string
  activityId?: string
  placeholder?: string
  keys?: boolean
  label?: string
  element?: unknown
}

// ── Component ─────────────────────────────────────────────────────────────────

export function VariableComboBoxEntry({
  id,
  mode,
  getValue,
  setValue,
  sourceKeys,
  processId,
  activityId,
  placeholder = 'Type or pick…',
  keys = false,
  label,
}: Props) {
  const [query, setQuery] = useState('')
  const [chips, setChips] = useState<Chip[]>([])
  const [focusedId, setFocusedId] = useState<string | undefined>(undefined)
  const [inputFocused, setInputFocused] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  // Parse initial value on mount
  useEffect(() => {
    const raw = getValue()
    if (!raw) return
    const parsed: Chip[] = raw.split(',').map((s) => s.trim()).filter(Boolean).map((v) => ({
      id: v,
      label: v,
    }))
    setChips(parsed)
  }, [])

  const { groups } = useVariableSources(sourceKeys, { processId, activityId })

  const filtered = filterGroups(groups, query)

  // Flatten filtered items for keyboard nav
  const flatItems = filtered.flatMap((g) => g.items)

  // Reset focused item when filtered list changes
  useEffect(() => {
    setFocusedId(flatItems[0]?.id)
  }, [filtered.length, query])

  const commitItem = useCallback((item: GroupItem) => {
    const newChips = mode === 'single'
      ? [{ id: item.id, label: item.name }]
      : chips.some((c) => c.id === item.id)
        ? chips
        : [...chips, { id: item.id, label: item.name }]
    setChips(newChips)
    setValue(newChips.map((c) => c.id).join(','))
    setQuery('')
    inputRef.current?.focus()
  }, [chips, mode, setValue])

  const commitLiteral = useCallback(() => {
    if (!query.trim()) return
    const val = query.trim()
    if (val.includes(',')) return
    const newChip: Chip = { id: val, label: val, warn: true }
    const newChips = mode === 'single' ? [newChip] : [...chips, newChip]
    setChips(newChips)
    setValue(newChips.map((c) => c.id).join(','))
    setQuery('')
  }, [query, chips, mode, setValue])

  const removeChip = useCallback((chipId: string) => {
    const next = chips.filter((c) => c.id !== chipId)
    setChips(next)
    setValue(next.map((c) => c.id).join(','))
  }, [chips, setValue])

  const removeLast = useCallback(() => {
    if (chips.length === 0) return
    const next = chips.slice(0, -1)
    setChips(next)
    setValue(next.map((c) => c.id).join(','))
  }, [chips, setValue])

  const showLiteralEscape =
    flatItems.length === 0 && query.trim().length > 0

  const handleKeyDown = useCallback((e: KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setFocusedId((prev) => {
        if (!prev) return flatItems[0]?.id
        const idx = flatItems.findIndex((it) => it.id === prev)
        return flatItems[idx + 1]?.id ?? flatItems[0]?.id
      })
      return
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault()
      setFocusedId((prev) => {
        if (!prev) return flatItems[flatItems.length - 1]?.id
        const idx = flatItems.findIndex((it) => it.id === prev)
        return flatItems[idx - 1]?.id ?? flatItems[flatItems.length - 1]?.id
      })
      return
    }
    if (e.key === 'Enter') {
      e.preventDefault()
      const item = flatItems.find((it) => it.id === focusedId)
      if (item) {
        commitItem(item)
      } else if (showLiteralEscape) {
        commitLiteral()
      }
      return
    }
    if (e.key === 'Escape') {
      e.preventDefault()
      setQuery('')
      inputRef.current?.blur()
      return
    }
    if (e.key === 'Backspace' && !query) {
      e.preventDefault()
      removeLast()
    }
  }, [flatItems, focusedId, query, commitItem, commitLiteral, showLiteralEscape, removeLast])

  const listboxId = `wf-combo-lb-${id}`

  return h('div', { class: 'bio-properties-panel-entry' },
    label && h('label', { class: 'bio-properties-panel-label' }, label),
    h('div', { class: 'wf-combo' },

      keys && h('div', { class: 'wf-combo-keys' },
        h('span', null, h('kbd', null, 'type'), ' filter all sections'),
        h('span', null, h('kbd', null, '↑'), h('kbd', null, '↓'), ' navigate'),
        h('span', null, h('kbd', null, 'Enter'), ' commit'),
        h('span', null, h('kbd', null, 'Esc'), ' close'),
        mode === 'multi' && h('span', null, h('kbd', null, '⌫'), ' on empty input removes last chip'),
      ),

      h('div', {
        class: `wf-combo-input${inputFocused ? ' focused' : ''}`,
        onClick: () => inputRef.current?.focus(),
      },
        ...chips.map((c) =>
          h('span', { key: c.id, class: `wf-combo-chip${c.warn ? ' warn' : ''}` },
            c.label,
            h('span', {
              class: 'x',
              role: 'button',
              'aria-label': `Remove ${c.label}`,
              onClick: (e: MouseEvent) => { e.stopPropagation(); removeChip(c.id) },
            }, '×')
          )
        ),
        h('input', {
          ref: inputRef,
          class: 'wf-combo-text',
          role: 'combobox',
          'aria-expanded': inputFocused && (filtered.length > 0 || showLiteralEscape),
          'aria-controls': listboxId,
          'aria-activedescendant': focusedId,
          value: query,
          placeholder: chips.length === 0 ? placeholder : '',
          autocomplete: 'off',
          onInput: (e: Event) => setQuery((e.target as HTMLInputElement).value),
          onKeyDown: handleKeyDown,
          onFocus: () => setInputFocused(true),
          onBlur: () => setInputFocused(false),
        })
      ),

      filtered.length > 0 && !showLiteralEscape && h('div', { class: 'wf-combo-dd', id: listboxId, role: 'listbox' },
        ...filtered.map((g) => h(Fragment, { key: g.key },
          h('div', { class: 'wf-combo-grp-hd', role: 'group', 'aria-label': g.label },
            h('span', { class: 'lbl' },
              h('span', { class: 'ic' }, h(GroupIcon, { kind: g.icon })),
              g.label
            ),
            h('span', { class: `cnt${query && g.total ? ' match' : ''}` },
              query && g.total ? `${g.items.length} OF ${g.total}` : (g.total ?? g.items.length)
            )
          ),
          ...g.items.map((item) => {
            const isFocused = item.id === focusedId
            const itemKind = item.kind ?? g.icon
            return h('div', {
              key: item.id,
              id: item.id,
              class: `wf-combo-itm${isFocused ? ' focus' : ''}`,
              role: 'option',
              'aria-selected': isFocused,
              onClick: () => commitItem(item),
            },
              h('span', { class: `ic ${itemKind}` }, h(GroupIcon, { kind: itemKind })),
              h('span', { class: `name${item.sans ? ' sans' : ''}` }, highlight(item.name, query)),
              item.tier && h('span', { class: 'wf-combo-tier' }, item.tier),
              item.lock && h('svg', { class: 'lock', ...ICON_PROPS },
                h('rect', { x: 3, y: 11, width: 18, height: 11, rx: 2 }),
                h('path', { d: 'M7 11V7a5 5 0 0 1 10 0v4' })
              ),
              item.meta && h('span', { class: 'meta' }, item.meta),
              h('span', { class: 'enter-hint' }, '↵ enter'),
            )
          })
        ))
      ),

      showLiteralEscape && h('div', { class: 'wf-combo-dd', id: listboxId, role: 'listbox' },
        h('div', { class: 'wf-combo-empty' },
          h('div', { class: 'msg' }, `No matches in PSS or DTDS scope for ${query}.`),
          h('div', { class: 'esc' },
            h('svg', { width: 13, height: 13, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': 2 },
              h('path', { d: 'M9 11l3 3L22 4' }),
              h('path', { d: 'M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11' })
            ),
            'Press ',
            h('kbd', null, 'Enter'),
            ' to use as a literal value'
          )
        )
      ),
    )
  )
}

export default VariableComboBoxEntry
