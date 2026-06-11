// ─── Tag colour palette ───────────────────────────────────────────────────────
export const TAG_PALETTE: Record<string, string> = {
  'Approval':    '#2563eb',
  'Legal':       '#7c3aed',
  'Operations':  '#0891b2',
  'Procurement': '#059669',
  'Finance':     '#d97706',
  'HR':          '#dc2626',
  'IT':          '#6b7280',
  'Onboarding':  '#0d9488',
  'Cap-Ex':      '#b45309',
  'Expense':     '#c026d3',
}

const FALLBACK_COLORS = [
  '#2563eb', '#7c3aed', '#059669', '#d97706',
  '#dc2626', '#0891b2', '#c026d3', '#b45309',
]

const ACCENT = '#149ba5'

export function slugifyTag(raw: string): string {
  const segment = raw.split('/').filter(Boolean).pop() ?? raw
  return segment.replace(/-/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase())
}

export function tagColor(tag: string): string {
  if (TAG_PALETTE[tag]) return TAG_PALETTE[tag]
  let hash = 0
  for (let i = 0; i < tag.length; i++) hash = (hash * 31 + tag.charCodeAt(i)) >>> 0
  return FALLBACK_COLORS[hash % FALLBACK_COLORS.length]
}

export function primaryColorForProcess(tags: string[]): string {
  return tags.length > 0 ? tagColor(tags[0]) : ACCENT
}

export interface ProcessDef {
  id: string
  key: string
  name: string
  version: number
  deploymentId: string
  owningDepartment?: string
  category?: string
  hasStartFormKey?: boolean
  startFormKey?: string
  hasDmn?: boolean
  hasConnector?: boolean
  hasNotification?: boolean
}

export function getProcessTags(process: ProcessDef): string[] {
  const seen = new Set<string>()
  const tags: string[] = []
  for (const raw of [process.owningDepartment, process.category]) {
    if (!raw) continue
    const label = slugifyTag(raw)
    if (!seen.has(label)) { seen.add(label); tags.push(label) }
  }
  return tags
}
