import type { LocaleEntry } from '@/lib/platform/types'

/** Formats a numeric amount using tenant locale settings. */
export function formatCurrency(amount: number, locale: LocaleEntry | undefined): string {
  if (!locale) return String(amount)
  return new Intl.NumberFormat(locale.numberFormat, {
    style: 'currency',
    currency: locale.currencyCode,
    maximumFractionDigits: 0,
  }).format(amount)
}

/** Returns a display hint string like "≤ ₹10,00,000" for DMN threshold cells. */
export function formatDmnThreshold(amount: number, locale: LocaleEntry | undefined): string {
  return `≤ ${formatCurrency(amount, locale)}`
}
