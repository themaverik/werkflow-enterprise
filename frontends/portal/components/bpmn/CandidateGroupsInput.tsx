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

const CHIP_STYLE =
  'display:inline-flex;align-items:center;gap:2px;background:#e3f2fd;border:1px solid #1565c0;' +
  'border-radius:3px;padding:1px 4px;font-size:11px;color:#1565c0;white-space:nowrap;'
const MANAGER_CHIP_STYLE =
  'display:inline-flex;align-items:center;gap:2px;background:#f3e5f5;border:1px solid #7b1fa2;' +
  'border-radius:3px;padding:1px 4px;font-size:11px;color:#7b1fa2;white-space:nowrap;'
const BTN_STYLE =
  'background:none;border:none;cursor:pointer;color:inherit;padding:0 0 0 2px;font-size:12px;line-height:1;'
const TAG_BOX_STYLE =
  'padding:4px;min-height:32px;height:auto;display:flex;flex-wrap:wrap;gap:4px;align-items:center;'
const GROUP_LABEL_STYLE =
  'font-size:10px;font-weight:600;text-transform:uppercase;color:#666;padding:2px 4px;width:100%;'

/**
 * Tag-select component for BPMN candidate groups.
 * Groups options by tier (SYSTEM / BUSINESS) per ADR-010.
 * No department-routing groups (_APPROVER) are included — filtered at PSS level.
 * Rendered by the bpmn-js properties panel (Preact).
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

  const unselectedGroups = availableGroups.filter((g) => !current.includes(g.key))
  const tier1Unselected = unselectedGroups.filter((g) => g.tier === 1)
  const tier2Unselected = unselectedGroups.filter((g) => g.tier === 2)

  const chips = current.map((key) => {
    const group = availableGroups.find((g) => g.key === key)
    const label = group?.label ?? key
    const isManager = group?.isManagerTier ?? false
    const chipStyle = isManager ? MANAGER_CHIP_STYLE : CHIP_STYLE
    return h('span', { key, style: chipStyle },
      label,
      isManager ? h('span', { style: 'font-size:9px;margin-left:2px;opacity:0.8' }, '[mgr]') : null,
      h('button', { type: 'button', onClick: () => toggle(key), style: BTN_STYLE }, '×')
    )
  })

  const buildOptGroup = (label: string, groups: CandidateGroupEntry[]) =>
    groups.length > 0
      ? h('optgroup', { label },
          ...groups.map((g) =>
            h('option', { key: g.key, value: g.key },
              g.isManagerTier ? `${g.label} [manager-tier]` : g.label
            )
          )
        )
      : null

  const dropdown = unselectedGroups.length > 0
    ? h('select', {
        class: 'bio-properties-panel-input',
        value: '',
        onChange: (e: Event) => {
          const val = (e.target as HTMLSelectElement).value
          if (val) {
            toggle(val);
            (e.target as HTMLSelectElement).value = ''
          }
        },
        style: 'margin-top:4px;',
      },
        h('option', { value: '' }, 'Add group...'),
        buildOptGroup('System Roles (Tier 1)', tier1Unselected),
        buildOptGroup('Business Groups (Tier 2)', tier2Unselected)
      )
    : current.length === 0
      ? h('select', {
          class: 'bio-properties-panel-input',
          disabled: true,
          style: 'margin-top:4px;',
        },
          h('option', { value: '' }, 'No groups available')
        )
      : null

  return h(Fragment, null,
    h('div', { class: 'bio-properties-panel-entry' },
      h('label', { class: 'bio-properties-panel-label' }, 'Candidate Groups'),
      h('div', { class: 'bio-properties-panel-textfield', style: TAG_BOX_STYLE }, ...chips),
      dropdown
    )
  )
}

export default CandidateGroupsInput
