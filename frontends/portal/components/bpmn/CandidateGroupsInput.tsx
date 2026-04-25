import { h, Fragment } from 'preact'

interface Props {
  id: string
  getValue: () => string
  setValue: (value: string) => void
  availableGroups: Array<{ id: string; name: string }>
  label?: string
  element?: any
}

const CHIP_STYLE =
  'display:inline-flex;align-items:center;gap:2px;background:#e3f2fd;border:1px solid #1565c0;' +
  'border-radius:3px;padding:1px 4px;font-size:11px;color:#1565c0;white-space:nowrap;'
const BTN_STYLE =
  'background:none;border:none;cursor:pointer;color:#1565c0;padding:0 0 0 2px;font-size:12px;line-height:1;'
const TAG_BOX_STYLE =
  'padding:4px;min-height:32px;height:auto;display:flex;flex-wrap:wrap;gap:4px;align-items:center;'

/**
 * Tag-select component for BPMN candidate groups.
 * Rendered by the bpmn-js properties panel (Preact) — uses Preact h() directly to avoid
 * JSX pragma conflicts with Next.js React automatic JSX transform.
 */
export function CandidateGroupsInput({ getValue, setValue, availableGroups }: Props) {
  const raw = getValue()
  const current: string[] = raw ? raw.split(',').map((s) => s.trim()).filter(Boolean) : []

  const toggle = (groupId: string) => {
    const next = current.includes(groupId)
      ? current.filter((g) => g !== groupId)
      : [...current, groupId]
    setValue(next.join(','))
  }

  const unselected = availableGroups.filter((g) => !current.includes(g.id))

  const chips = current.map((id) => {
    const group = availableGroups.find((g) => g.id === id)
    const label = group?.name || id
    return h('span', { key: id, style: CHIP_STYLE },
      label,
      h('button', {
        type: 'button',
        onClick: () => toggle(id),
        style: BTN_STYLE,
      }, '×')
    )
  })

  const dropdown = unselected.length > 0
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
        ...unselected.map((g) =>
          h('option', { key: g.id, value: g.id },
            g.name ? `${g.name} (${g.id})` : g.id
          )
        )
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
