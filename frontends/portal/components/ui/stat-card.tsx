import type { LucideIcon } from 'lucide-react'

interface StatCardProps {
  icon: LucideIcon
  label: string
  value: number | string
  iconColor: string
  sub?: string
}

export function StatCard({ icon: Icon, label, value, iconColor, sub }: StatCardProps) {
  return (
    <div className="bg-card border border-border rounded-xl p-5 flex items-center gap-4">
      <div
        className="w-11 h-11 rounded-xl flex items-center justify-center shrink-0"
        style={{ background: iconColor + '18' }}
      >
        <Icon size={20} style={{ color: iconColor }} strokeWidth={1.8} />
      </div>
      <div>
        <div className="text-2xl font-bold text-foreground leading-none">{value}</div>
        <div className="text-xs text-muted-foreground mt-1">{label}</div>
        {sub && <div className="text-xs font-medium mt-0.5" style={{ color: iconColor }}>{sub}</div>}
      </div>
    </div>
  )
}
