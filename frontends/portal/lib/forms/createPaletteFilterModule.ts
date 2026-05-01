/* eslint-disable @typescript-eslint/no-explicit-any */

export function createPaletteFilterModule(allowedTypes: string[]) {
  function PaletteFilterModule(formFields: any) {
    const originalInit = formFields.init?.bind(formFields) as ((...args: unknown[]) => void) | undefined
    formFields.init = function (...args: unknown[]) {
      originalInit?.(...args)
      const allTypes: string[] = formFields._formFields
        ? Object.keys((formFields._formFields as { _types?: Record<string, unknown> })._types ?? {})
        : []
      allTypes
        .filter((t) => !allowedTypes.includes(t))
        .forEach((t: string) => (formFields as { deregister: (t: string) => void }).deregister(t))
    }
  }
  PaletteFilterModule.$inject = ['formFields']
  return {
    __init__: ['paletteFilter'],
    paletteFilter: ['type', PaletteFilterModule],
  }
}
