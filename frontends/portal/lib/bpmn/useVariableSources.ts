import { useState, useEffect } from 'react'
import { adminApiClient } from '@/lib/api/client'
import type { Group, GroupItem } from '@/components/ui/VariableComboBox'

// ── Cache ─────────────────────────────────────────────────────────────────────

const TTL_MS = 5 * 60 * 1000

interface CacheEntry {
  data: Group[]
  ts: number
}

const cache = new Map<string, CacheEntry>()

function getCached(key: string): Group[] | null {
  const entry = cache.get(key)
  if (!entry) return null
  if (Date.now() - entry.ts > TTL_MS) {
    cache.delete(key)
    return null
  }
  return entry.data
}

function setCached(key: string, data: Group[]): void {
  cache.set(key, { data, ts: Date.now() })
}

// ── Source fetchers ───────────────────────────────────────────────────────────

interface CandidateGroupApiItem {
  key: string
  label?: string
  kind?: string
  tier?: number
  isManagerTier?: boolean
}

async function fetchPssCandidateGroups(): Promise<Group[]> {
  const cacheKey = 'pss-candidate-groups'
  const cached = getCached(cacheKey)
  if (cached) return cached

  try {
    const res = await adminApiClient.get<CandidateGroupApiItem[] | { groups?: CandidateGroupApiItem[] }>(
      '/api/v1/platform/candidate-groups'
    )
    const raw: CandidateGroupApiItem[] = Array.isArray(res.data)
      ? res.data
      : (res.data as { groups?: CandidateGroupApiItem[] }).groups ?? []

    const system: GroupItem[] = []
    const business: GroupItem[] = []

    for (const g of raw) {
      const item: GroupItem = {
        id: g.key,
        name: g.label ?? g.key,
        sans: true,
        tier: g.isManagerTier ? 'manager-tier' : undefined,
        lock: g.kind === 'SYSTEM' || g.tier === 1,
      }
      if (g.kind === 'SYSTEM' || g.tier === 1) {
        system.push(item)
      } else {
        business.push(item)
      }
    }

    const groups: Group[] = []
    if (system.length > 0) {
      groups.push({ key: 'system', label: 'System · Tier 1 · read-only', icon: 'system', items: system })
    }
    if (business.length > 0) {
      groups.push({ key: 'business', label: 'Business · Tier 2', icon: 'business', items: business })
    }

    setCached(cacheKey, groups)
    return groups
  } catch {
    return []
  }
}

interface ProcessVariableApiItem {
  name: string
  type?: string
  setByActivity?: string
  setByTask?: string
}

async function fetchDtdsVariablesString(processId: string, activityId: string): Promise<Group[]> {
  const cacheKey = JSON.stringify(['dtds-variables-string', processId, activityId])
  const cached = getCached(cacheKey)
  if (cached) return cached

  try {
    const res = await adminApiClient.get<{ variables?: ProcessVariableApiItem[] }>(
      `/api/v1/design/bpmn/processes/${encodeURIComponent(processId)}/variables-at/${encodeURIComponent(activityId)}`
    )
    const variables = res.data.variables ?? []
    const stringVars = variables.filter((v) => !v.type || v.type === 'string')

    const items: GroupItem[] = stringVars.map((v) => ({
      id: `\${${v.name}}`,
      name: `\${${v.name}}`,
      meta: v.setByTask ? `from ${v.setByTask}` : v.setByActivity ? `from ${v.setByActivity}` : undefined,
    }))

    const groups: Group[] = items.length > 0
      ? [{ key: 'process', label: 'Process Variables · in scope', icon: 'process', items }]
      : []

    setCached(cacheKey, groups)
    return groups
  } catch {
    return []
  }
}

interface FeelExpressionApiItem {
  key: string
  label?: string
  pattern?: string
}

interface FeelExpressionApiResponse {
  custodyVars?: {
    groupResolutions?: FeelExpressionApiItem[]
  }
  expressions?: FeelExpressionApiItem[]
}

async function fetchCustodyFeel(): Promise<Group[]> {
  const cacheKey = 'custody-feel'
  const cached = getCached(cacheKey)
  if (cached) return cached

  try {
    const res = await adminApiClient.get<FeelExpressionApiResponse>(
      '/api/v1/platform/feel-expressions',
      { params: { type: 'custody' } }
    )
    const data = res.data

    const resolutions: FeelExpressionApiItem[] = data.custodyVars?.groupResolutions ?? []
    const items: GroupItem[] = resolutions.map((r) => ({
      id: `\${custodyVars.${r.key}}`,
      name: `\${custodyVars.${r.key}}`,
      meta: r.pattern ? `→ ${r.pattern}` : undefined,
    }))

    items.push({
      id: '${custodyVars[itemCategory]}',
      name: '${custodyVars[itemCategory]}',
      meta: 'by variable',
    })

    const groups: Group[] = items.length > 0
      ? [{ key: 'custody', label: 'Custody Lookups · ADR-004', icon: 'custody', items }]
      : []

    setCached(cacheKey, groups)
    return groups
  } catch {
    return []
  }
}

async function fetchDtdsVariablesDate(processId: string, activityId: string): Promise<Group[]> {
  const cacheKey = JSON.stringify(['dtds-variables-date', processId, activityId])
  const cached = getCached(cacheKey)
  if (cached) return cached

  try {
    const res = await adminApiClient.get<{ variables?: ProcessVariableApiItem[] }>(
      `/api/v1/design/bpmn/processes/${encodeURIComponent(processId)}/variables-at/${encodeURIComponent(activityId)}`
    )
    const variables = res.data.variables ?? []
    const dateVars = variables.filter((v) => v.type === 'date' || v.type === 'dateTime' || v.type === 'duration')

    const items: GroupItem[] = dateVars.map((v) => ({
      id: `\${${v.name}}`,
      name: `\${${v.name}}`,
      meta: v.setByTask ? `from ${v.setByTask}` : undefined,
    }))

    const groups: Group[] = items.length > 0
      ? [{ key: 'process-date', label: 'Process Variables · date/duration', icon: 'process', items }]
      : []

    setCached(cacheKey, groups)
    return groups
  } catch {
    return []
  }
}

async function fetchDtdsVariablesNumber(processId: string, activityId: string): Promise<Group[]> {
  const cacheKey = JSON.stringify(['dtds-variables-number', processId, activityId])
  const cached = getCached(cacheKey)
  if (cached) return cached

  try {
    const res = await adminApiClient.get<{ variables?: ProcessVariableApiItem[] }>(
      `/api/v1/design/bpmn/processes/${encodeURIComponent(processId)}/variables-at/${encodeURIComponent(activityId)}`
    )
    const variables = res.data.variables ?? []
    const numberVars = variables.filter((v) => v.type === 'number' || v.type === 'integer' || v.type === 'long')

    const items: GroupItem[] = numberVars.map((v) => ({
      id: `\${${v.name}}`,
      name: `\${${v.name}}`,
      meta: v.setByTask ? `from ${v.setByTask}` : undefined,
    }))

    const groups: Group[] = items.length > 0
      ? [{ key: 'process-number', label: 'Process Variables · number', icon: 'process', items }]
      : []

    setCached(cacheKey, groups)
    return groups
  } catch {
    return []
  }
}

interface SlaConstantApiItem {
  key: string
  label?: string
  value?: string
  description?: string
}

async function fetchSlaConstants(): Promise<Group[]> {
  const cacheKey = 'sla-constants'
  const cached = getCached(cacheKey)
  if (cached) return cached

  try {
    const res = await adminApiClient.get<unknown>('/api/v1/platform/sla-constants')
    const data = res.data as { constants?: SlaConstantApiItem[] } | null
    const constants: SlaConstantApiItem[] = data?.constants ?? []

    const items: GroupItem[] = constants.map((item) => ({
      id: item.key,
      name: item.label ?? item.key,
      meta: item.value ?? item.description,
      sans: true,
    }))

    const groups: Group[] = items.length > 0
      ? [{ key: 'sla', label: 'SLA Constants', icon: 'feel', items }]
      : []

    setCached(cacheKey, groups)
    return groups
  } catch {
    return []
  }
}

interface PriorityConstantApiItem {
  key: string
  label?: string
  value?: number
}

async function fetchPriorityConstants(): Promise<Group[]> {
  const cacheKey = 'priority-constants'
  const cached = getCached(cacheKey)
  if (cached) return cached

  try {
    const res = await adminApiClient.get<unknown>('/api/v1/platform/priorities')
    const data = res.data as { priorities?: PriorityConstantApiItem[] } | null
    const priorities: PriorityConstantApiItem[] = data?.priorities ?? []

    const items: GroupItem[] = priorities.map((item) => ({
      id: String(item.value ?? item.key),
      name: item.label ?? item.key,
      meta: item.value !== undefined ? String(item.value) : undefined,
      sans: true,
    }))

    const groups: Group[] = items.length > 0
      ? [{ key: 'priorities', label: 'Priority Constants', icon: 'system', items }]
      : []

    setCached(cacheKey, groups)
    return groups
  } catch {
    return []
  }
}

interface DeployedFormApiItem {
  key: string
  name?: string
  version?: number
}

async function fetchFormsDeployed(): Promise<Group[]> {
  const cacheKey = 'forms-deployed'
  const cached = getCached(cacheKey)
  if (cached) return cached

  try {
    const res = await adminApiClient.get<unknown>('/api/v1/design/forms')
    const data = res.data as { forms?: DeployedFormApiItem[] } | DeployedFormApiItem[] | null
    const forms: DeployedFormApiItem[] = Array.isArray(data)
      ? data
      : (data as { forms?: DeployedFormApiItem[] } | null)?.forms ?? []

    const items: GroupItem[] = forms.map((item) => ({
      id: item.key,
      name: item.name ?? item.key,
      meta: item.version !== undefined ? `v${item.version}` : undefined,
      sans: true,
    }))

    const groups: Group[] = items.length > 0
      ? [{ key: 'forms', label: 'Deployed Forms', icon: 'literal', items }]
      : []

    setCached(cacheKey, groups)
    return groups
  } catch {
    return []
  }
}

// ── Hook ──────────────────────────────────────────────────────────────────────

interface UseVariableSourcesContext {
  processId?: string
  activityId?: string
}

interface UseVariableSourcesResult {
  groups: Group[]
  loading: boolean
  error: string | null
}

export function useVariableSources(
  sourceKeys: string[],
  context: UseVariableSourcesContext
): UseVariableSourcesResult {
  const [groups, setGroups] = useState<Group[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)

    const fetches = sourceKeys.map((key): Promise<Group[]> => {
      switch (key) {
        case 'pss-candidate-groups':
          return fetchPssCandidateGroups()

        case 'dtds-variables-string':
          if (!context.processId || !context.activityId) return Promise.resolve([])
          return fetchDtdsVariablesString(context.processId, context.activityId)

        case 'custody-feel':
          return fetchCustodyFeel()

        case 'dtds-variables-date':
          if (!context.processId || !context.activityId) return Promise.resolve([])
          return fetchDtdsVariablesDate(context.processId, context.activityId)

        case 'dtds-variables-number':
          if (!context.processId || !context.activityId) return Promise.resolve([])
          return fetchDtdsVariablesNumber(context.processId, context.activityId)

        case 'sla-constants':
          return fetchSlaConstants()

        case 'priority-constants':
          return fetchPriorityConstants()

        case 'forms-deployed':
          return fetchFormsDeployed()

        default:
          return Promise.resolve([])
      }
    })

    Promise.all(fetches)
      .then((results) => {
        if (cancelled) return
        setGroups(results.flat())
        setLoading(false)
      })
      .catch((err: unknown) => {
        if (cancelled) return
        setError('Failed to load variable sources')
        setLoading(false)
      })

    return () => { cancelled = true }
  }, [sourceKeys.join(','), context.processId, context.activityId])

  return { groups, loading, error }
}
