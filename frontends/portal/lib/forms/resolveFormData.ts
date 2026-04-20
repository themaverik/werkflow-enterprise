export async function resolveFormData(
  schema: any,
  fetchFn: (url: string, params?: Record<string, any>) => Promise<any[]>,
  onError?: (url: string, err: unknown) => void
): Promise<Record<string, any>> {
  const components: any[] = schema?.components ?? []
  const tasks: Promise<void>[] = []
  const data: Record<string, any> = {}

  for (const component of components) {
    const ds = component.properties?.dataSource
    const key = ds?.valuesKey ?? component.valuesKey
    if (!ds || !key || ds.dependsOn) continue

    const params = { ...ds.filter }

    tasks.push(
      fetchFn(ds.url, params)
        .then((items) => {
          data[key] = items.map((item) => ({
            label: item[ds.labelField],
            value: item[ds.valueField],
            ...Object.fromEntries((ds.extraFields ?? []).map((f: string) => [f, item[f]])),
          }))
        })
        .catch((err) => {
          data[key] = []
          onError?.(ds.url, err)
        })
    )
  }

  await Promise.all(tasks)
  return data
}

export async function resolveDependentData(
  schema: any,
  changedKey: string,
  changedValue: any,
  fetchFn: (url: string, params?: Record<string, any>) => Promise<any[]>,
  onError?: (url: string, err: unknown) => void
): Promise<Record<string, any>> {
  const components: any[] = schema?.components ?? []
  const patch: Record<string, any> = {}
  const tasks: Promise<void>[] = []

  function clearDescendants(parentFieldKey: string) {
    for (const component of components) {
      const ds = component.properties?.dataSource
      if (!ds || ds.dependsOn !== parentFieldKey) continue
      const optionsKey = ds.valuesKey ?? component.valuesKey
      patch[component.key] = null
      if (optionsKey) patch[optionsKey] = []
      clearDescendants(component.key)
    }
  }

  for (const component of components) {
    const ds = component.properties?.dataSource
    if (!ds || ds.dependsOn !== changedKey) continue

    const optionsKey = ds.valuesKey ?? component.valuesKey
    patch[component.key] = null

    if (changedValue == null || changedValue === '') {
      if (optionsKey) patch[optionsKey] = []
      clearDescendants(component.key)
      continue
    }

    clearDescendants(component.key)

    if (optionsKey) {
      const params = { ...ds.filter, [ds.dependsOnParam]: changedValue }
      tasks.push(
        fetchFn(ds.url, params)
          .then((items) => {
            patch[optionsKey] = items.map((item) => ({
              label: item[ds.labelField],
              value: item[ds.valueField],
              ...Object.fromEntries((ds.extraFields ?? []).map((f: string) => [f, item[f]])),
            }))
          })
          .catch((err) => {
            patch[optionsKey] = []
            onError?.(ds.url, err)
          })
      )
    }
  }

  await Promise.all(tasks)
  return patch
}
