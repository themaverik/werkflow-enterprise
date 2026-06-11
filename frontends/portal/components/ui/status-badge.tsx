type Status = 'active' | 'completed' | 'suspended' | 'failed' | 'draft' | string

const STATUS_STYLES: Record<string, { bg: string; color: string; border: string; label: string }> = {
  active:    { bg: 'var(--badge-blue-bg)',    color: 'var(--badge-blue)',    border: 'var(--badge-blue-border)',    label: 'Active' },
  completed: { bg: 'var(--badge-success-bg)', color: 'var(--badge-success)', border: 'var(--badge-success-border)', label: 'Completed' },
  suspended: { bg: 'var(--badge-warning-bg)', color: 'var(--badge-warning)', border: 'var(--badge-warning-border)', label: 'Suspended' },
  failed:    { bg: 'var(--badge-danger-bg)',  color: 'var(--badge-danger)',  border: 'var(--badge-danger-border)',  label: 'Failed' },
  draft:     { bg: 'var(--badge-neutral-bg)', color: 'var(--badge-neutral)', border: 'var(--badge-neutral-border)', label: 'Draft' },
  UP:        { bg: 'var(--badge-success-bg)', color: 'var(--badge-success)', border: 'var(--badge-success-border)', label: 'Healthy' },
  DOWN:      { bg: 'var(--badge-danger-bg)',  color: 'var(--badge-danger)',  border: 'var(--badge-danger-border)',  label: 'Down' },
  DEGRADED:  { bg: 'var(--badge-warning-bg)', color: 'var(--badge-warning)', border: 'var(--badge-warning-border)', label: 'Degraded' },
}

export function StatusBadge({ status }: { status: Status }) {
  const s = STATUS_STYLES[status] ?? {
    bg: '#f8fafc', color: '#475569', border: '#e2e8f0', label: status,
  }
  return (
    <span
      className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border"
      style={{ background: s.bg, color: s.color, borderColor: s.border }}
    >
      {s.label}
    </span>
  )
}
