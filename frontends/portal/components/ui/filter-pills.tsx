interface FilterOption {
  key: string
  label: string
  count?: number
}

interface FilterPillsProps {
  options: FilterOption[]
  active: string
  onChange: (key: string) => void
}

export function FilterPills({ options, active, onChange }: FilterPillsProps) {
  return (
    <div className="flex gap-2 flex-wrap">
      {options.map((opt) => (
        <button
          key={opt.key}
          onClick={() => onChange(opt.key)}
          className={`inline-flex items-center gap-1.5 px-4 py-1.5 rounded-full text-sm font-medium transition-colors ${
            active === opt.key
              ? 'bg-primary text-primary-foreground'
              : 'bg-card border border-border text-muted-foreground hover:bg-muted'
          }`}
        >
          {opt.label}
          {opt.count !== undefined && (
            <span className={`text-xs rounded-full px-1.5 py-0.5 ${
              active === opt.key ? 'bg-white/20 text-white' : 'bg-muted text-muted-foreground'
            }`}>
              {opt.count}
            </span>
          )}
        </button>
      ))}
    </div>
  )
}
