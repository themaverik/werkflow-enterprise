import { h, Fragment } from 'preact'
import type { CandidateGroupEntry } from '@/lib/platform/types'
import type { ProcessVariable } from '@/lib/api/dtds'

interface Props {
  id: string
  getValue: () => string
  setValue: (value: string) => void
  availableGroups: CandidateGroupEntry[]
  processVariables: ProcessVariable[]
  custodyVarGroups: Array<{ key: string; label: string; pattern: string }>
  label?: string
  element?: unknown
}

// ── Inline style constants (Preact — no Tailwind) ───────────────
// Exact color values from design spec README chip table.

const FEEL_CHIP: Record<string, string> = {
  background: '#faece7',
  color: '#712b13',
  border: '1px solid #f3d3c5',
}
const BUSINESS_CHIP: Record<string, string> = {
  background: '#eeedfe',
  color: '#3c3489',
  border: '1px solid #d9d6f5',
}
const SYSTEM_CHIP: Record<string, string> = {
  background: '#f1efe8',
  color: '#2c2c2a',
  border: '1px solid #e3e0d4',
}

const CHIP_BASE: Record<string, string> = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: '4px',
  padding: '3px 8px',
  borderRadius: '6px',
  fontSize: '11px',
  fontFamily: "'JetBrains Mono', monospace",
  lineHeight: '1.4',
  whiteSpace: 'nowrap',
}

const REMOVE_BTN: Record<string, string> = {
  background: 'none',
  border: 'none',
  cursor: 'pointer',
  color: 'inherit',
  padding: '0',
  lineHeight: '1',
  fontSize: '12px',
  opacity: '0.7',
  display: 'flex',
  alignItems: 'center',
}

const TAG_BOX: Record<string, string> = {
  padding: '6px',
  minHeight: '32px',
  height: 'auto',
  display: 'flex',
  flexWrap: 'wrap',
  gap: '4px',
  alignItems: 'center',
  border: '1px solid #e2eaee',
  borderRadius: '6px',
  background: 'var(--wf-bg, #ffffff)',
}

const SUGGEST_LIST: Record<string, string> = {
  border: '1px solid #e2eaee',
  borderRadius: '6px',
  overflow: 'hidden',
  background: 'var(--wf-bg, #ffffff)',
  listStyle: 'none',
  padding: '0',
  margin: '0',
  marginTop: '6px',
}

const GROUP_HEADER: Record<string, string> = {
  fontSize: '10px',
  color: '#6b7e8c',
  textTransform: 'uppercase',
  letterSpacing: '0.05em',
  padding: '7px 10px 4px',
  background: '#f0f4f6',
  borderTop: '1px solid #eef2f5',
  fontWeight: '600',
}

const SUGGEST_ITEM_BASE: Record<string, string> = {
  padding: '7px 10px',
  fontSize: '11.5px',
  display: 'flex',
  alignItems: 'center',
  gap: '7px',
  borderTop: '1px solid #eef2f5',
  cursor: 'pointer',
  lineHeight: '1.4',
  color: '#0f1e2a',
}

const TIER_BADGE: Record<string, string> = {
  fontSize: '9px',
  padding: '1px 5px',
  background: '#fff7ed',
  color: '#9a4f06',
  borderRadius: '4px',
  fontFamily: "'JetBrains Mono', monospace",
  border: '1px solid #fde6c8',
  flexShrink: '0',
}

const NOTE_INFO: Record<string, string> = {
  padding: '8px 10px',
  borderRadius: '6px',
  fontSize: '11px',
  lineHeight: '1.5',
  display: 'flex',
  gap: '6px',
  alignItems: 'flex-start',
  background: '#e1f5ee',
  color: '#085041',
  border: '1px solid #c0e6d6',
  marginTop: '8px',
}

// ── Dot indicator per kind ──
const DOT_COLORS: Record<string, string> = {
  feel:     '#9a4f06',
  business: '#5b48b8',
  system:   '#6b7e8c',
}

/**
 * Returns a human-readable source annotation for a process variable.
 * Prefers setByTask (display name), falls back to activity ID heuristics.
 */
function getVariableSource(v: ProcessVariable): string {
  if (v.setByTask) return `from ${v.setByTask}`
  const id = (v.setByActivity ?? '').toLowerCase()
  if (id.includes('startevent') || id.includes('start_')) return 'from process start'
  if (id.includes('dmn') || id.includes('decision') || id.includes('businessrule')) return 'from DMN'
  return `from ${v.setByActivity ?? 'upstream'}`
}

/**
 * Tag-select component for BPMN candidate groups.
 * Renders four data-sourced sections:
 *   1. Process Variables · in scope (from DTDS variables-at)
 *   2. Custody Lookups (from PSS feel-expressions custodyVars)
 *   3. Business · Tier 2 (from CandidateGroupEntry tier=2)
 *   4. System · Tier 1 · read-only (from CandidateGroupEntry tier=1)
 * Rendered by the bpmn-js properties panel (Preact). Uses inline styles only — no Tailwind.
 */
export function CandidateGroupsInput({ getValue, setValue, availableGroups, processVariables, custodyVarGroups }: Props) {
  const raw = getValue()
  const current: string[] = raw ? raw.split(',').map((s) => s.trim()).filter(Boolean) : []

  const toggle = (groupKey: string) => {
    const next = current.includes(groupKey)
      ? current.filter((g) => g !== groupKey)
      : [...current, groupKey]
    setValue(next.join(','))
  }

  // Chips for currently selected values
  const chips = current.map((key) => {
    const group = availableGroups.find((g) => g.key === key)
    const label = group?.label ?? key
    const isManager = group?.isManagerTier ?? false
    const isFeel = key.startsWith('${') || key.startsWith('custodyVars')
    const chipStyle = isFeel ? { ...CHIP_BASE, ...FEEL_CHIP }
      : isManager ? { ...CHIP_BASE, ...BUSINESS_CHIP }
      : { ...CHIP_BASE, ...SYSTEM_CHIP }
    return h('span', { key, style: chipStyle },
      label,
      isManager
        ? h('span', { style: { fontSize: '9px', opacity: '0.8', marginLeft: '2px' } }, '[mgr]')
        : null,
      h('button', { type: 'button', onClick: () => toggle(key), style: REMOVE_BTN, 'aria-label': `Remove ${label}` }, '×')
    )
  })

  // Helper: a single suggest-list item row
  const buildItem = (
    key: string,
    label: string,
    kind: 'feel' | 'business' | 'system',
    meta?: string,
    badge?: string,
    readOnly?: boolean
  ) => {
    const isChecked = current.includes(key)
    const itemStyle = isChecked
      ? { ...SUGGEST_ITEM_BASE, background: '#e6f1fb', color: '#0c447c' }
      : SUGGEST_ITEM_BASE
    return h('li', {
      key,
      style: itemStyle,
      role: 'option',
      'aria-selected': isChecked,
      onClick: readOnly ? undefined : () => toggle(key),
    },
      h('span', {
        'aria-hidden': 'true',
        style: {
          width: '6px',
          height: '6px',
          borderRadius: '50%',
          background: DOT_COLORS[kind],
          flexShrink: '0',
        },
      }),
      h('span', {
        style: {
          fontFamily: "'JetBrains Mono', monospace",
          fontSize: '11px',
          flex: '1',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        },
      }, label),
      badge
        ? h('span', { style: TIER_BADGE }, badge)
        : null,
      readOnly
        ? h('span', {
            'aria-label': 'read-only',
            style: { fontSize: '10px', color: '#a8b9c4', flexShrink: '0' },
          }, '🔒')
        : null,
      meta
        ? h('span', {
            style: {
              marginLeft: 'auto',
              fontSize: '10px',
              color: isChecked ? '#185fa5' : '#6b7e8c',
              flexShrink: '0',
            },
          }, meta)
        : null
    )
  }

  const buildGroupHeader = (title: string, isFirst: boolean, subtitle?: string) =>
    h('li', {
      role: 'presentation',
      style: isFirst ? { ...GROUP_HEADER, borderTop: 'none' } : GROUP_HEADER,
    },
      title,
      subtitle
        ? h('span', {
            style: {
              display: 'block',
              fontSize: '9px',
              color: '#8a9caa',
              fontWeight: '400',
              letterSpacing: '0',
              textTransform: 'none',
              marginTop: '1px',
            },
          }, subtitle)
        : null
    )

  // ── Section 1: Process Variables · in scope ──
  const processVarSection = processVariables.length > 0
    ? h(Fragment, null,
        buildGroupHeader('Process Variables · in scope', true, 'string-typed, resolved at runtime'),
        ...processVariables.map((v) =>
          buildItem(`\${${v.name}}`, `\${${v.name}}`, 'feel', getVariableSource(v))
        )
      )
    : null

  // ── Section 2: Custody Lookups ──
  const custodySection = custodyVarGroups.length > 0
    ? h(Fragment, null,
        buildGroupHeader('Custody Lookups', processVariables.length === 0, 'Computed groups · ADR-004'),
        ...custodyVarGroups.map((g) =>
          buildItem(g.key, g.label, 'feel', g.pattern)
        )
      )
    : null

  // ── Section 3: Business · Tier 2 ──
  const tier2 = availableGroups.filter((g) => g.tier === 2)
  const tier2Section = tier2.length > 0
    ? h(Fragment, null,
        buildGroupHeader('Business · Tier 2', !processVarSection && !custodySection),
        ...tier2.map((g) =>
          buildItem(g.key, g.label, 'business', undefined, g.isManagerTier ? 'manager-tier' : undefined)
        )
      )
    : null

  // ── Section 4: System · Tier 1 — read-only ──
  const tier1 = availableGroups.filter((g) => g.tier === 1)
  const tier1Section = tier1.length > 0
    ? h(Fragment, null,
        buildGroupHeader('System · Tier 1 · read-only', !processVarSection && !custodySection && !tier2Section),
        ...tier1.map((g) =>
          buildItem(g.key, g.label, 'system', undefined, undefined, true)
        )
      )
    : null

  const suggestList = h('ul', {
    role: 'listbox',
    'aria-label': 'Candidate group options',
    style: SUGGEST_LIST,
  },
    processVarSection,
    custodySection,
    tier2Section,
    tier1Section
  )

  // Informational note
  const note = h('div', { style: NOTE_INFO, role: 'note' },
    h('span', { 'aria-hidden': 'true', style: { flexShrink: '0', marginTop: '1px' } }, 'ℹ'),
    h('span', null,
      'Process variables come from forms (Key fields), DMN outputs, service tasks, and process-start delegates. DTDS computes scope from upstream BPMN nodes.'
    )
  )

  return h(Fragment, null,
    h('div', { class: 'bio-properties-panel-entry' },
      h('label', { class: 'bio-properties-panel-label' }, 'Candidate Groups'),
      h('div', {
        class: 'bio-properties-panel-textfield',
        style: chips.length > 0 ? TAG_BOX : { ...TAG_BOX, minHeight: '32px' },
      }, ...chips),
      suggestList,
      note
    )
  )
}

export default CandidateGroupsInput
