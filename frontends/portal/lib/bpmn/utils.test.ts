import { describe, it, expect } from 'vitest'
import { generateProcessId, generateBlankBpmn, extractProcessId } from './utils'

describe('generateProcessId', () => {
  it('produces an NCName-valid key that is not the hardcoded "process"', () => {
    const id = generateProcessId()
    expect(id).toMatch(/^Process_[a-z0-9]+$/)
    expect(id).not.toBe('process')
  })

  it('returns a distinct id on every call (no collisions)', () => {
    const ids = new Set(Array.from({ length: 50 }, () => generateProcessId()))
    expect(ids.size).toBe(50)
  })
})

describe('generateBlankBpmn', () => {
  it('defaults to a unique process id, never the literal "process"', () => {
    const key1 = extractProcessId(generateBlankBpmn())
    const key2 = extractProcessId(generateBlankBpmn())
    expect(key1).not.toBe('process')
    expect(key1).toMatch(/^Process_/)
    expect(key1).not.toBe(key2)
  })

  it('honours an explicit processId when provided', () => {
    expect(extractProcessId(generateBlankBpmn('leave-request'))).toBe('leave-request')
  })
})
