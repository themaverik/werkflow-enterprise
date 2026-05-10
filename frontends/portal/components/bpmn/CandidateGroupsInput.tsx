import { h, Fragment } from 'preact'
import type { CandidateGroupEntry } from '@/lib/platform/types'

interface Props {
  id: string
  getValue: () => string
  setValue: (value: string) => void
  availableGroups: CandidateGroupEntry[]
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
 * Tag-select component for BPMN candidate groups.
 * Renders three sections: FEEL expressions, Business (Tier 2), System (Tier 1 — read-only).
 * No DEPARTMENT or DOA groups per ADR-010.
 * Rendered by the bpmn-js properties panel (Preact). Uses inline styles only — no Tailwind.
 */
export function CandidateGroupsInput({ getValue, setValue, availableGroups }: Props) {
  const raw = getValue()
  const current: string[] = raw ? raw.split(',').map((s) => s.trim()).filter(Boolean) : []

  const toggle = (groupKey: string) => {
    const next = current.includes(groupKey)
      ? current.filter((g) => g !== groupKey)
      : [...current, groupKey]
    setValue(next.join(','))
  }

  // The FEEL routing variable is always first in the list as the primary suggestion
  const feelEntry = { key: '${assignedGroup}', label: '${assignedGroup}', kind: 'FEEL' as const, meta: 'DMN result' }
  const custodyFeel = availableGroups.filter((g) => (g as unknown as { kind: string }).kind === 'FEEL')
  const tier2Groups = availableGroups.filter((g) => g.tier === 2)
  const tier1Groups = availableGroups.filter((g) => g.tier === 1 && g.readOnly)

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

  const buildGroupHeader = (title: string, isFirst: boolean) =>
    h('li', {
      role: 'presentation',
      style: isFirst
        ? { ...GROUP_HEADER, borderTop: 'none' }
        : GROUP_HEADER,
    }, title)

  const suggestList = h('ul', {
    role: 'listbox',
    'aria-label': 'Candidate group options',
    style: SUGGEST_LIST,
  },
    // FEEL EXPRESSIONS section
    buildGroupHeader('FEEL Expressions', true),
    buildItem(feelEntry.key, feelEntry.label, 'feel', feelEntry.meta),
    ...custodyFeel.map((g) =>
      buildItem(g.key, g.label, 'feel', 'custody')
    ),
    // BUSINESS Tier 2 section
    tier2Groups.length > 0
      ? h(Fragment, null,
          buildGroupHeader('Business · Tier 2', false),
          ...tier2Groups.map((g) =>
            buildItem(
              g.key,
              g.label,
              'business',
              undefined,
              g.isManagerTier ? 'manager-tier' : undefined
            )
          )
        )
      : null,
    // SYSTEM Tier 1 — read-only
    tier1Groups.length > 0
      ? h(Fragment, null,
          buildGroupHeader('System · Tier 1 · read-only', false),
          ...tier1Groups.map((g) =>
            buildItem(g.key, g.label, 'system', undefined, undefined, true)
          )
        )
      : null
  )

  // Informational note
  const note = h('div', { style: NOTE_INFO, role: 'note' },
    h('span', { 'aria-hidden': 'true', style: { flexShrink: '0', marginTop: '1px' } }, 'ℹ'),
    h('span', null,
      'No DEPARTMENT or DOA groups — routing happens in the upstream DMN (ADR-010)'
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
