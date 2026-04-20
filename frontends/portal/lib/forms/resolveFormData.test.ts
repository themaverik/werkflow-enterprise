import { describe, it, expect, vi } from 'vitest'
import { resolveFormData, resolveDependentData } from './resolveFormData'

const categoryOptions = [
  { id: 1, name: 'IT', custodianDeptCode: 'IT-DEPT' },
  { id: 2, name: 'Facilities', custodianDeptCode: 'FAC-DEPT' },
]
const assetOptions = [
  { id: 10, name: 'Laptop', categoryId: 1 },
  { id: 11, name: 'Chair', categoryId: 2 },
]

const schema = {
  components: [
    {
      type: 'select',
      key: 'categoryId',
      valuesKey: 'categoryOptions',
      properties: {
        dataSource: {
          url: '/api/v1/asset-categories',
          labelField: 'name',
          valueField: 'id',
          filter: { active: true },
        },
      },
    },
    {
      type: 'select',
      key: 'assetDefinitionId',
      properties: {
        dataSource: {
          url: '/api/v1/asset-definitions',
          labelField: 'name',
          valueField: 'id',
          extraFields: ['categoryId'],
          filter: { active: true },
          dependsOn: 'categoryId',
          dependsOnParam: 'categoryId',
          valuesKey: 'assetDefinitions',
        },
      },
    },
  ],
}

// ─── resolveFormData ────────────────────────────────────────────────────────

describe('resolveFormData', () => {
  it('fetches independent selects and returns keyed data', async () => {
    const fetchFn = vi.fn().mockResolvedValue(categoryOptions)
    const result = await resolveFormData(schema, fetchFn)
    expect(fetchFn).toHaveBeenCalledWith('/api/v1/asset-categories', { active: true })
    expect(result.categoryOptions).toEqual([
      { label: 'IT', value: 1 },
      { label: 'Facilities', value: 2 },
    ])
    // dependent select is NOT fetched on load
    expect(fetchFn).toHaveBeenCalledTimes(1)
  })

  it('skips dependent selects (dependsOn set)', async () => {
    const fetchFn = vi.fn().mockResolvedValue([])
    await resolveFormData(schema, fetchFn)
    expect(fetchFn).toHaveBeenCalledTimes(1)
  })

  it('returns empty array and calls onError when fetch fails', async () => {
    const fetchFn = vi.fn().mockRejectedValue(new Error('network error'))
    const onError = vi.fn()
    const result = await resolveFormData(schema, fetchFn, onError)
    expect(result.categoryOptions).toEqual([])
    expect(onError).toHaveBeenCalledWith('/api/v1/asset-categories', expect.any(Error))
  })

  it('includes extraFields in mapped options', async () => {
    const schemaWithExtras = {
      components: [
        {
          type: 'select',
          key: 'assetDefinitionId',
          valuesKey: 'assetDefinitions',
          properties: {
            dataSource: {
              url: '/api/v1/asset-definitions',
              labelField: 'name',
              valueField: 'id',
              extraFields: ['categoryId'],
            },
          },
        },
      ],
    }
    const fetchFn = vi.fn().mockResolvedValue(assetOptions)
    const result = await resolveFormData(schemaWithExtras, fetchFn)
    expect(result.assetDefinitions[0]).toEqual({ label: 'Laptop', value: 10, categoryId: 1 })
  })
})

// ─── resolveDependentData ───────────────────────────────────────────────────

describe('resolveDependentData', () => {
  it('fetches direct dependent options and resets selected value when parent changes', async () => {
    const fetchFn = vi.fn().mockResolvedValue(assetOptions)
    const patch = await resolveDependentData(schema, 'categoryId', 1, fetchFn)
    expect(patch.assetDefinitionId).toBeNull()
    expect(patch.assetDefinitions).toEqual([
      { label: 'Laptop', value: 10, categoryId: 1 },
      { label: 'Chair', value: 11, categoryId: 2 },
    ])
    expect(fetchFn).toHaveBeenCalledWith('/api/v1/asset-definitions', { active: true, categoryId: 1 })
  })

  it('clears options and resets selected value when parent cleared', async () => {
    const fetchFn = vi.fn()
    const patch = await resolveDependentData(schema, 'categoryId', null, fetchFn)
    expect(patch.assetDefinitionId).toBeNull()
    expect(patch.assetDefinitions).toEqual([])
    expect(fetchFn).not.toHaveBeenCalled()
  })

  it('clears options and resets selected value when parent set to empty string', async () => {
    const fetchFn = vi.fn()
    const patch = await resolveDependentData(schema, 'categoryId', '', fetchFn)
    expect(patch.assetDefinitionId).toBeNull()
    expect(patch.assetDefinitions).toEqual([])
    expect(fetchFn).not.toHaveBeenCalled()
  })

  it('clears all grandchildren selected values and options in a 3-level cascade', async () => {
    const threeLevel = {
      components: [
        {
          type: 'select',
          key: 'level1Id',
          valuesKey: 'level1Options',
          properties: {
            dataSource: {
              url: '/api/level1',
              labelField: 'name',
              valueField: 'id',
            },
          },
        },
        {
          type: 'select',
          key: 'level2Id',
          properties: {
            dataSource: {
              url: '/api/level2',
              labelField: 'name',
              valueField: 'id',
              dependsOn: 'level1Id',
              dependsOnParam: 'level1Id',
              valuesKey: 'level2Options',
            },
          },
        },
        {
          type: 'select',
          key: 'level3Id',
          properties: {
            dataSource: {
              url: '/api/level3',
              labelField: 'name',
              valueField: 'id',
              dependsOn: 'level2Id',
              dependsOnParam: 'level2Id',
              valuesKey: 'level3Options',
            },
          },
        },
      ],
    }
    const fetchFn = vi.fn().mockResolvedValue([{ id: 99, name: 'Item' }])
    const patch = await resolveDependentData(threeLevel, 'level1Id', 'europe', fetchFn)

    // Direct dependent: value cleared, options fetched
    expect(patch.level2Id).toBeNull()
    expect(patch.level2Options).toEqual([{ label: 'Item', value: 99 }])
    // Grandchild: value cleared, options cleared, no fetch
    expect(patch.level3Id).toBeNull()
    expect(patch.level3Options).toEqual([])
    expect(fetchFn).toHaveBeenCalledTimes(1) // only level2, not level3
  })

  it('returns empty array and calls onError when dependent fetch fails', async () => {
    const fetchFn = vi.fn().mockRejectedValue(new Error('timeout'))
    const onError = vi.fn()
    const patch = await resolveDependentData(schema, 'categoryId', 1, fetchFn, onError)
    expect(patch.assetDefinitions).toEqual([])
    expect(onError).toHaveBeenCalledWith('/api/v1/asset-definitions', expect.any(Error))
  })
})
