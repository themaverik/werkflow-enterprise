import React from 'react'

export type GroupKind = 'process' | 'custody' | 'business' | 'system' | 'feel' | 'literal'

export type GroupItem = {
  id: string
  name: string
  sans?: boolean
  kind?: GroupKind
  meta?: string
  tier?: string
  lock?: boolean
}

export type Group = {
  key: string
  label: string
  icon: GroupKind
  items: GroupItem[]
  total?: number
}

export type Chip = {
  id: string
  label: string
  kind?: GroupKind
  warn?: boolean
}

export type Source = {
  kind: 'pss' | 'dtds'
  path: string
}

export interface VariableComboBoxProps {
  mode: 'multi' | 'single'
  placeholder?: string
  query: string
  onQuery: (q: string) => void
  chips?: Chip[]
  onRemoveChip?: (id: string) => void
  groups: Group[]
  focusedId?: string
  onItemClick?: (item: GroupItem) => void
  literalEscape?: { value: string; hint: string } | null
  sources?: Source[]
  keys?: boolean
  contextLine?: React.ReactNode
}

// ── SVG icon helpers ────────────────────────────────────────────────────────

const ICON_PROPS = {
  width: 11,
  height: 11,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 2,
} as const

function ComboGroupIcon({ kind }: { kind: GroupKind }) {
  switch (kind) {
    case 'process':
      return <svg {...ICON_PROPS}><path d="M4 7v-2h16v2M9 20h6M12 4v16"/></svg>
    case 'custody':
      return <svg {...ICON_PROPS}><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
    case 'business':
      return <svg {...ICON_PROPS}><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/></svg>
    case 'system':
      return <svg {...ICON_PROPS}><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
    case 'feel':
      return <svg {...ICON_PROPS}><path d="M3 3l18 18M3 21L21 3"/></svg>
    case 'literal':
      return <svg {...ICON_PROPS}><path d="M3 21l4-4 5 5 9-9M14 4h6v6"/></svg>
    default:
      return null
  }
}

function ComboItemIcon({ kind }: { kind: GroupKind }) {
  switch (kind) {
    case 'feel':
    case 'process':
      return <svg {...ICON_PROPS}><path d="M4 7v-2h16v2M9 20h6M12 4v16"/></svg>
    case 'custody':
      return <svg {...ICON_PROPS}><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
    case 'business':
      return <svg {...ICON_PROPS}><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/></svg>
    case 'system':
      return <svg {...ICON_PROPS}><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
    case 'literal':
      return <svg {...ICON_PROPS}><path d="M3 21l4-4 5 5 9-9M14 4h6v6"/></svg>
    default:
      return null
  }
}

// ── Substring highlight ───────────────────────────────────────────────────────

function Highlight({ text, query }: { text: string; query: string }) {
  if (!query) return <>{text}</>
  const idx = text.toLowerCase().indexOf(query.toLowerCase())
  if (idx === -1) return <>{text}</>
  return (
    <>
      {text.slice(0, idx)}
      <span className="hl">{text.slice(idx, idx + query.length)}</span>
      {text.slice(idx + query.length)}
    </>
  )
}

// ── Main component ────────────────────────────────────────────────────────────

export function VariableComboBox({
  mode,
  placeholder = 'Type or pick…',
  query,
  onQuery,
  chips = [],
  onRemoveChip,
  groups,
  focusedId,
  onItemClick,
  literalEscape = null,
  sources = [],
  keys = false,
  contextLine,
}: VariableComboBoxProps) {
  const [focused, setFocused] = React.useState(false)

  const nonEmpty = groups.filter((g) => g.items.length > 0)
  const hiddenLabels = groups
    .filter((g) => g.items.length === 0 && query && (g.total ?? 0) > 0)
    .map((g) => g.label)

  const listboxId = React.useId()
  const inputId = React.useId()

  return (
    <div className="wf-combo">
      {keys && (
        <div className="wf-combo-keys">
          <span><kbd>type</kbd> filter all sections</span>
          <span><kbd>↑</kbd><kbd>↓</kbd> navigate</span>
          <span><kbd>Enter</kbd> commit</span>
          <span><kbd>Esc</kbd> close</span>
          {mode === 'multi' && <span><kbd>⌫</kbd> on empty input removes last chip</span>}
        </div>
      )}

      {contextLine && (
        <div style={{ fontSize: 10, fontWeight: 700, color: 'var(--wf-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 2 }}>
          {contextLine}
        </div>
      )}

      <div
        className={`wf-combo-input${focused ? ' focused' : ''}`}
        onClick={() => {
          const el = document.getElementById(inputId)
          el?.focus()
        }}
      >
        {chips.map((c) => (
          <span key={c.id} className={`wf-combo-chip${c.warn ? ' warn' : ''}`}>
            {c.label}
            <span
              className="x"
              role="button"
              aria-label={`Remove ${c.label}`}
              onClick={(e) => {
                e.stopPropagation()
                onRemoveChip?.(c.id)
              }}
            >
              ×
            </span>
          </span>
        ))}
        <input
          id={inputId}
          className="wf-combo-text"
          role="combobox"
          aria-expanded={focused && (nonEmpty.length > 0 || !!literalEscape)}
          aria-controls={listboxId}
          aria-activedescendant={focusedId}
          value={query}
          placeholder={chips.length === 0 ? placeholder : ''}
          onChange={(e) => onQuery(e.target.value)}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
          autoComplete="off"
        />
      </div>

      {nonEmpty.length > 0 && !literalEscape && (
        <div className="wf-combo-dd" id={listboxId} role="listbox">
          {nonEmpty.map((g) => (
            <React.Fragment key={g.key}>
              <div className="wf-combo-grp-hd" role="group" aria-label={g.label}>
                <span className="lbl">
                  <span className="ic">
                    <ComboGroupIcon kind={g.icon} />
                  </span>
                  {g.label}
                </span>
                <span className={`cnt${query && g.total ? ' match' : ''}`}>
                  {query && g.total
                    ? `${g.items.length} OF ${g.total}`
                    : (g.total ?? g.items.length)}
                </span>
              </div>
              {g.items.map((item) => {
                const isFocused = item.id === focusedId
                const itemKind = item.kind ?? g.icon
                return (
                  <div
                    key={item.id}
                    id={item.id}
                    className={`wf-combo-itm${isFocused ? ' focus' : ''}`}
                    role="option"
                    aria-selected={isFocused}
                    onClick={() => onItemClick?.(item)}
                  >
                    <span className={`ic ${itemKind}`}>
                      <ComboItemIcon kind={itemKind} />
                    </span>
                    <span className={`name${item.sans ? ' sans' : ''}`}>
                      <Highlight text={item.name} query={query} />
                    </span>
                    {item.tier && <span className="wf-combo-tier">{item.tier}</span>}
                    {item.lock && (
                      <svg className="lock" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <rect x="3" y="11" width="18" height="11" rx="2"/>
                        <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                      </svg>
                    )}
                    {item.meta && <span className="meta">{item.meta}</span>}
                    <span className="enter-hint">↵ enter</span>
                  </div>
                )
              })}
            </React.Fragment>
          ))}
        </div>
      )}

      {literalEscape && (
        <div className="wf-combo-dd" id={listboxId} role="listbox">
          <div className="wf-combo-empty">
            <div className="msg">
              {literalEscape.hint || `No matches in PSS or DTDS scope for ${literalEscape.value}.`}
            </div>
            <div className="esc">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M9 11l3 3L22 4"/>
                <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
              </svg>
              Press <kbd>Enter</kbd> to use as a literal value
            </div>
          </div>
        </div>
      )}

      {hiddenLabels.length > 0 && (
        <p style={{ fontSize: 10, fontStyle: 'italic', color: 'var(--wf-muted)', margin: 0 }}>
          {hiddenLabels.join(' and ')} {hiddenLabels.length === 1 ? 'section' : 'sections'} hidden — no matches for &ldquo;{query}&rdquo;
        </p>
      )}

      {sources.length > 0 && (
        <div className="wf-combo-sources">
          {sources.map((s, i) => (
            <span key={i} className={`src ${s.kind}`}>
              <svg width="9" height="9" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M21 8L12 4 3 8l9 4 9-4z"/>
                <path d="M3 12l9 4 9-4M3 16l9 4 9-4"/>
              </svg>
              {s.kind.toUpperCase()} · {s.path}
            </span>
          ))}
        </div>
      )}
    </div>
  )
}

export default VariableComboBox
