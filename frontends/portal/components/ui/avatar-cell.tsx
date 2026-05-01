interface AvatarCellProps {
  name: string
  size?: number
}

function initials(name: string): string {
  return name
    .split(' ')
    .slice(0, 2)
    .map((n) => n[0])
    .join('')
    .toUpperCase()
}

const AVATAR_COLORS = [
  '#149ba5', '#1d4ed8', '#16a34a', '#c27b00', '#dc2626',
  '#0891b2', '#6366f1', '#f59e0b',
]

function colorFor(name: string): string {
  let h = 0
  for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) >>> 0
  return AVATAR_COLORS[h % AVATAR_COLORS.length]
}

export function AvatarCell({ name, size = 28 }: AvatarCellProps) {
  const bg = colorFor(name)
  return (
    <span
      className="inline-flex items-center justify-center rounded-full text-white font-semibold shrink-0"
      style={{ width: size, height: size, fontSize: size * 0.36, background: bg }}
      title={name}
    >
      {initials(name)}
    </span>
  )
}
