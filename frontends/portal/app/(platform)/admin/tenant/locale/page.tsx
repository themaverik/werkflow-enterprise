'use client'

import { useState, useEffect } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { useAuthorization } from '@/lib/auth/use-authorization'
import { useLocale } from '@/lib/platform/usePlatformCapabilities'
import { formatCurrency } from '@/lib/locale/currency'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { toast } from 'sonner'
import type { LocaleEntry } from '@/lib/platform/types'
import { PageSurface } from '@/components/layout/page-surface'

interface CurrencyOption {
  code: string
  symbol: string
  label: string
  defaultLocale: string
  defaultNumberFormat: string
}

const CURRENCY_OPTIONS: CurrencyOption[] = [
  { code: 'INR', symbol: '₹', label: 'Indian Rupee (₹)', defaultLocale: 'en-IN', defaultNumberFormat: 'en-IN' },
  { code: 'USD', symbol: '$', label: 'US Dollar ($)',     defaultLocale: 'en-US', defaultNumberFormat: 'en-US' },
  { code: 'EUR', symbol: '€', label: 'Euro (€)',          defaultLocale: 'de-DE', defaultNumberFormat: 'de-DE' },
  { code: 'GBP', symbol: '£', label: 'British Pound (£)', defaultLocale: 'en-GB', defaultNumberFormat: 'en-GB' },
  { code: 'SGD', symbol: 'S$', label: 'Singapore Dollar (S$)', defaultLocale: 'en-SG', defaultNumberFormat: 'en-SG' },
  { code: 'AED', symbol: 'د.إ', label: 'UAE Dirham (د.إ)', defaultLocale: 'ar-AE', defaultNumberFormat: 'en-AE' },
]

const TIMEZONE_OPTIONS = [
  'UTC',
  'Asia/Kolkata',
  'Asia/Singapore',
  'Asia/Dubai',
  'America/New_York',
  'America/Los_Angeles',
  'Europe/London',
  'Europe/Berlin',
]

const DATE_FORMAT_OPTIONS = ['DD/MM/YYYY', 'MM/DD/YYYY', 'YYYY-MM-DD']

const PREVIEW_AMOUNT = 1_000_000

interface FormState {
  currencyCode: string
  locale: string
  numberFormat: string
  dateFormat: string
  timezone: string
}

function buildLocaleEntry(form: FormState): LocaleEntry {
  return {
    currencyCode: form.currencyCode,
    locale: form.locale,
    numberFormat: form.numberFormat,
    dateFormat: form.dateFormat,
    timezone: form.timezone,
  }
}

export default function LocalePage() {
  const { data: session, status } = useSession()
  const { hasAnyRole } = useAuthorization()
  const token = (session?.accessToken as string) ?? ''
  const qc = useQueryClient()

  const { data: current, isLoading } = useLocale()

  const [form, setForm] = useState<FormState>({
    currencyCode: 'USD',
    locale: 'en-US',
    numberFormat: 'en-US',
    dateFormat: 'MM/DD/YYYY',
    timezone: 'UTC',
  })

  useEffect(() => {
    if (current) {
      setForm({
        currencyCode: current.currencyCode,
        locale: current.locale,
        numberFormat: current.numberFormat,
        dateFormat: current.dateFormat,
        timezone: current.timezone,
      })
    }
  }, [current])

  const saveMutation = useMutation({
    mutationFn: async (entry: LocaleEntry) => {
      const res = await fetch('/api/proxy/admin/config/vars', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({
          varKey: 'tenantLocale',
          varValue: JSON.stringify(entry),
          varType: 'LOCALE',
          description: 'Tenant locale and currency configuration',
        }),
      })
      if (res.status === 409) {
        // var already exists — update by fetching the id first then PUT
        const listRes = await fetch('/api/proxy/admin/config/vars?type=LOCALE', {
          headers: { Authorization: `Bearer ${token}` },
        })
        if (!listRes.ok) throw new Error('Failed to load existing locale config')
        const vars = await listRes.json() as Array<{ id: number; varKey: string }>
        const existing = vars.find((v) => v.varKey === 'tenantLocale')
        if (!existing) throw new Error('Locale var not found after conflict')
        const putRes = await fetch(`/api/proxy/admin/config/vars/${existing.id}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
          body: JSON.stringify({
            varKey: 'tenantLocale',
            varValue: JSON.stringify(entry),
            varType: 'LOCALE',
            description: 'Tenant locale and currency configuration',
          }),
        })
        if (!putRes.ok) throw new Error('Failed to update locale config')
        return putRes.json()
      }
      if (!res.ok) throw new Error('Failed to save locale config')
      return res.json()
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['pss', 'locale'] })
      qc.invalidateQueries({ queryKey: ['pss', 'capabilities'] })
      toast.success('Locale settings saved')
    },
    onError: (e: Error) => toast.error(e.message),
  })

  if (!hasAnyRole(['ADMIN', 'SUPER_ADMIN'])) {
    return <div className="p-6 text-muted-foreground">Access denied.</div>
  }

  const handleCurrencyChange = (code: string) => {
    const opt = CURRENCY_OPTIONS.find((c) => c.code === code)
    if (!opt) return
    setForm((prev) => ({
      ...prev,
      currencyCode: opt.code,
      locale: opt.defaultLocale,
      numberFormat: opt.defaultNumberFormat,
    }))
  }

  const previewEntry = buildLocaleEntry(form)

  return (
    <PageSurface className="max-w-2xl">
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-semibold">Locale &amp; Currency</h1>
        <p className="text-sm text-muted-foreground mt-1">
          Configure how amounts, dates, and timezones are displayed across the platform.
        </p>
      </div>

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading...</p>
      ) : (
        <div className="space-y-5">
          <div className="border rounded-md p-4 space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="currency" className="text-sm font-medium">Currency</Label>
              <select
                id="currency"
                value={form.currencyCode}
                onChange={(e) => handleCurrencyChange(e.target.value)}
                className="w-full border rounded-md px-3 py-2 text-sm bg-background focus:outline-none focus:ring-2 focus:ring-ring"
              >
                {CURRENCY_OPTIONS.map((opt) => (
                  <option key={opt.code} value={opt.code}>{opt.label}</option>
                ))}
              </select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="timezone" className="text-sm font-medium">Timezone</Label>
              <select
                id="timezone"
                value={form.timezone}
                onChange={(e) => setForm((prev) => ({ ...prev, timezone: e.target.value }))}
                className="w-full border rounded-md px-3 py-2 text-sm bg-background focus:outline-none focus:ring-2 focus:ring-ring"
              >
                {TIMEZONE_OPTIONS.map((tz) => (
                  <option key={tz} value={tz}>{tz}</option>
                ))}
              </select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="dateFormat" className="text-sm font-medium">Date Format</Label>
              <select
                id="dateFormat"
                value={form.dateFormat}
                onChange={(e) => setForm((prev) => ({ ...prev, dateFormat: e.target.value }))}
                className="w-full border rounded-md px-3 py-2 text-sm bg-background focus:outline-none focus:ring-2 focus:ring-ring"
              >
                {DATE_FORMAT_OPTIONS.map((fmt) => (
                  <option key={fmt} value={fmt}>{fmt}</option>
                ))}
              </select>
            </div>
          </div>

          <div className="border rounded-md p-4 bg-muted/30">
            <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide mb-1">Live Preview</p>
            <p className="text-sm">
              Amounts will display as:{' '}
              <span className="font-mono font-semibold">{formatCurrency(PREVIEW_AMOUNT, previewEntry)}</span>
            </p>
            <p className="text-xs text-muted-foreground mt-1">
              Timezone: {form.timezone} &mdash; Date format: {form.dateFormat}
            </p>
          </div>

          <Button
            onClick={() => saveMutation.mutate(previewEntry)}
            disabled={saveMutation.isPending || status !== 'authenticated'}
            size="sm"
          >
            {saveMutation.isPending ? 'Saving...' : 'Save Locale Settings'}
          </Button>
        </div>
      )}
    </div>
    </PageSurface>
  )
}
