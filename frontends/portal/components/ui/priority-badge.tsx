const PRIORITY_STYLES: Record<string, { bg: string; color: string; border: string; label: string }> = {
  urgent: { bg: 'var(--badge-danger-bg)',  color: 'var(--badge-danger)',  border: 'var(--badge-danger-border)',  label: 'Urgent' },
  high:   { bg: '#fff7ed',                 color: '#c2410c',              border: '#fed7aa',                    label: 'High' },
  medium: { bg: 'var(--badge-warning-bg)', color: 'var(--badge-warning)', border: 'var(--badge-warning-border)', label: 'Medium' },
  low:    { bg: 'var(--badge-success-bg)', color: 'var(--badge-success)', border: 'var(--badge-success-border)', label: 'Low' },
}

function priorityKey(value: number): string {
  if (value >= 100) return 'urgent'
  if (value >= 75) return 'high'
  if (value >= 50) return 'medium'
  return 'low'
}

export function PriorityBadge({ priority }: { priority: number }) {
  const key = priorityKey(priority)
  const s = PRIORITY_STYLES[key]
  return (
    <span
      className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border"
      style={{ background: s.bg, color: s.color, borderColor: s.border }}
    >
      {s.label}
    </span>
  )
}
