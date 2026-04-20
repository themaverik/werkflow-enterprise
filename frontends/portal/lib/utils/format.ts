/**
 * Format a date string for display in tables and detail views.
 * Returns '-' if the input is falsy.
 */
export function formatDate(dateStr?: string): string {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/**
 * Format a date string as a full locale string (date + time).
 * Returns empty string if the input is falsy.
 */
export function formatDateTime(dateStr?: string): string {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString()
}
