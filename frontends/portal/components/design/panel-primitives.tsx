'use client'

import { Info, AlertTriangle } from 'lucide-react'

// ── PssPill ──────────────────────────────────────────────────
// Renders the "PSS · /endpoint-name" hint badge below a field.
// Background #fff7ed, border #fde6c8, text #9a4f06, JetBrains Mono 10px.
interface PssPillProps {
  endpoint: string
}

export function PssPill({ endpoint }: PssPillProps): JSX.Element {
  return (
    <span
      className="inline-flex items-center gap-1 mt-1 leading-snug"
      style={{
        fontSize: '10px',
        padding: '2px 7px',
        background: '#fff7ed',
        color: '#9a4f06',
        borderRadius: '6px',
        fontFamily: "'JetBrains Mono', monospace",
        border: '1px solid #fde6c8',
      }}
    >
      <svg width="8" height="8" viewBox="0 0 8 8" fill="none" aria-hidden="true" style={{ flexShrink: 0 }}>
        <circle cx="4" cy="4" r="3" stroke="currentColor" strokeWidth="1.2" />
        <path d="M4 2.5v1.8l1 1" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
      </svg>
      PSS · {endpoint}
    </span>
  )
}

// ── FeelChip ─────────────────────────────────────────────────
// Colored chip for FEEL/system/business/dept/cat/tag entries.
// Each kind maps to exact bg/border/text from the design spec.
export type ChipKind = 'feel' | 'system' | 'business' | 'dept' | 'cat' | 'tag'

const CHIP_KIND_STYLES: Record<ChipKind, { bg: string; color: string; border: string; fontFamily?: string }> = {
  feel:     { bg: '#faece7', color: '#712b13', border: '#f3d3c5' },
  system:   { bg: '#f1efe8', color: '#2c2c2a', border: '#e3e0d4' },
  business: { bg: '#eeedfe', color: '#3c3489', border: '#d9d6f5' },
  dept:     { bg: '#e6f1fb', color: '#0c447c', border: '#c7dcf2' },
  cat:      { bg: '#e1f5ee', color: '#085041', border: '#c0e6d6' },
  tag:      { bg: '#fbeaf0', color: '#72243e', border: '#f3d4df', fontFamily: "'DM Sans', system-ui, sans-serif" },
}

interface FeelChipProps {
  label: string
  kind: ChipKind
  onRemove?: () => void
}

export function FeelChip({ label, kind, onRemove }: FeelChipProps): JSX.Element {
  const s = CHIP_KIND_STYLES[kind]
  const isTag = kind === 'tag'
  return (
    <span
      className="inline-flex items-center gap-1 leading-snug"
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: '4px',
        padding: '3px 8px',
        borderRadius: '6px',
        fontSize: '11px',
        fontFamily: s.fontFamily ?? "'JetBrains Mono', monospace",
        background: s.bg,
        color: s.color,
        border: `1px solid ${s.border}`,
      }}
    >
      {label}
      {onRemove && (
        <button
          type="button"
          onClick={onRemove}
          aria-label={`Remove ${label}`}
          style={{
            background: 'none',
            border: 'none',
            cursor: 'pointer',
            color: 'inherit',
            padding: '0',
            lineHeight: 1,
            fontSize: '12px',
            opacity: 0.7,
            display: 'flex',
            alignItems: 'center',
          }}
        >
          ×
        </button>
      )}
    </span>
  )
}

// ── SuggestList / SuggestGroup / SuggestItem ─────────────────
// Grouped suggestion/autocomplete list components.

interface SuggestItemProps {
  label: string
  meta?: string
  kind: 'feel' | 'business' | 'system'
  checked?: boolean
  badge?: string
  onClick: () => void
}

const ITEM_IC_COLORS: Record<'feel' | 'business' | 'system', string> = {
  feel:     '#9a4f06',
  system:   '#6b7e8c',
  business: '#5b48b8',
}

export function SuggestItem({ label, meta, kind, checked, badge, onClick }: SuggestItemProps): JSX.Element {
  return (
    <li
      role="option"
      aria-selected={checked ?? false}
      onClick={onClick}
      style={{
        padding: '7px 10px',
        fontSize: '11.5px',
        display: 'flex',
        alignItems: 'center',
        gap: '7px',
        borderTop: '1px solid #eef2f5',
        cursor: 'pointer',
        lineHeight: 1.4,
        color: checked ? '#0c447c' : '#0f1e2a',
        background: checked ? '#e6f1fb' : undefined,
      }}
      onMouseEnter={(e) => {
        if (!checked) (e.currentTarget as HTMLElement).style.background = '#f7fafb'
      }}
      onMouseLeave={(e) => {
        if (!checked) (e.currentTarget as HTMLElement).style.background = ''
      }}
    >
      {/* Kind indicator dot */}
      <span
        aria-hidden="true"
        style={{
          width: '6px',
          height: '6px',
          borderRadius: '50%',
          background: ITEM_IC_COLORS[kind],
          flexShrink: 0,
        }}
      />
      <span
        style={{
          fontFamily: "'JetBrains Mono', monospace",
          fontSize: '11px',
          flex: 1,
          minWidth: 0,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
      >
        {label}
      </span>
      {badge && (
        <span
          style={{
            fontSize: '9px',
            padding: '1px 5px',
            background: '#fff7ed',
            color: '#9a4f06',
            borderRadius: '4px',
            fontFamily: "'JetBrains Mono', monospace",
            border: '1px solid #fde6c8',
            flexShrink: 0,
          }}
        >
          {badge}
        </span>
      )}
      {meta && (
        <span
          style={{
            marginLeft: 'auto',
            fontSize: '10px',
            color: checked ? '#185fa5' : '#6b7e8c',
            display: 'flex',
            alignItems: 'center',
            gap: '4px',
            flexShrink: 0,
          }}
        >
          {meta}
        </span>
      )}
    </li>
  )
}

interface SuggestGroupProps {
  title: string
  children: React.ReactNode
}

export function SuggestGroup({ title, children }: SuggestGroupProps): JSX.Element {
  return (
    <>
      <li
        role="presentation"
        style={{
          fontSize: '10px',
          color: '#6b7e8c',
          textTransform: 'uppercase',
          letterSpacing: '0.05em',
          padding: '7px 10px 4px',
          background: '#f0f4f6',
          borderTop: '1px solid #eef2f5',
          fontWeight: 600,
        }}
      >
        {title}
      </li>
      {children}
    </>
  )
}

interface SuggestListProps {
  children: React.ReactNode
}

export function SuggestList({ children }: SuggestListProps): JSX.Element {
  return (
    <ul
      role="listbox"
      style={{
        border: '1px solid #e2eaee',
        borderRadius: '6px',
        overflow: 'hidden',
        background: '#ffffff',
        listStyle: 'none',
        padding: 0,
        margin: 0,
      }}
    >
      {children}
    </ul>
  )
}

// ── MetaRow ───────────────────────────────────────────────────
// Bordered selector field wrapping chips or selects.
// .bound: #e6f1fb bg, #0c447c text, #c7dcf2 border.

interface MetaRowProps {
  children: React.ReactNode
  bound?: boolean
}

export function MetaRow({ children, bound }: MetaRowProps): JSX.Element {
  return (
    <div
      style={{
        padding: '6px 9px',
        border: `1px solid ${bound ? '#c7dcf2' : '#e2eaee'}`,
        borderRadius: '6px',
        background: bound ? '#e6f1fb' : '#ffffff',
        color: bound ? '#0c447c' : undefined,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: '6px',
        fontSize: '12px',
        minHeight: '32px',
        flexWrap: 'wrap',
      }}
    >
      {children}
    </div>
  )
}

// ── Note ──────────────────────────────────────────────────────
// Inline advisory callout. Three variants from the design spec.

export type NoteVariant = 'info' | 'warn' | 'muted'

const NOTE_STYLES: Record<NoteVariant, { bg: string; color: string; border: string }> = {
  info:  { bg: '#e1f5ee', color: '#085041', border: '#c0e6d6' },
  warn:  { bg: '#faeeda', color: '#633806', border: '#f3dcb1' },
  muted: { bg: '#f0f4f6', color: '#6b7e8c', border: '#eef2f5' },
}

interface NoteProps {
  children: React.ReactNode
  variant: NoteVariant
}

export function Note({ children, variant }: NoteProps): JSX.Element {
  const s = NOTE_STYLES[variant]
  return (
    <div
      role="note"
      style={{
        padding: '8px 10px',
        borderRadius: '6px',
        fontSize: '11px',
        lineHeight: 1.5,
        display: 'flex',
        gap: '6px',
        alignItems: 'flex-start',
        background: s.bg,
        color: s.color,
        border: `1px solid ${s.border}`,
      }}
    >
      {variant === 'warn' ? (
        <AlertTriangle size={13} style={{ flexShrink: 0, marginTop: '1px' }} aria-hidden="true" />
      ) : (
        <Info size={13} style={{ flexShrink: 0, marginTop: '1px' }} aria-hidden="true" />
      )}
      <span>{children}</span>
    </div>
  )
}
