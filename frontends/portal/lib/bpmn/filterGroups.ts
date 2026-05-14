import type { Group, GroupItem } from '@/components/ui/VariableComboBox'

function matchesQuery(item: GroupItem, q: string): boolean {
  const lower = q.toLowerCase()
  return (
    item.name.toLowerCase().includes(lower) ||
    item.id.toLowerCase().includes(lower) ||
    (item.meta?.toLowerCase().includes(lower) ?? false)
  )
}

/**
 * Filters groups by a query string.
 * Each returned group has `total` set to its original item count
 * and `items` filtered to those matching the query.
 * Groups with zero matching items are excluded from the result.
 * When the query is empty all groups are returned with `total` set.
 */
export function filterGroups(groups: Group[], q: string): Group[] {
  if (!q) {
    return groups.map((g) => ({ ...g, total: g.items.length }))
  }

  return groups.reduce<Group[]>((acc, g) => {
    const filtered = g.items.filter((item) => matchesQuery(item, q))
    if (filtered.length === 0) return acc
    acc.push({ ...g, total: g.items.length, items: filtered })
    return acc
  }, [])
}
