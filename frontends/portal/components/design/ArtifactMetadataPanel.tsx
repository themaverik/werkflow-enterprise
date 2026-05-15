'use client'

import { useEffect, useRef, useState } from 'react'
import { useSession } from 'next-auth/react'
import { ChevronDown } from 'lucide-react'
import { usePlatformCapabilities, useCategories, useDepartments } from '@/lib/platform/usePlatformCapabilities'
import { platformApi } from '@/lib/platform/api'
import type { ArtifactMetadata, TagEntry } from '@/lib/platform/types'
import { FeelChip, MetaRow, Note } from './panel-primitives'

interface Props {
  artifactType: 'process' | 'form' | 'dmn'
  value: ArtifactMetadata
  onChange: (v: ArtifactMetadata) => void
}

/**
 * Shared metadata panel used by BPMN, Form, and DMN designers.
 * Provides department (visibility scoping), category (catalog), and tags (search).
 * Per ADR-010: department is visibility only — not a routing input.
 * Styled to match design spec (section 4 — Metadata · ADR-010).
 */
export function ArtifactMetadataPanel({ value, onChange }: Props) {
  const { data: session, status } = useSession()
  const token = (session?.accessToken as string) ?? ''

  const { data: capabilities } = usePlatformCapabilities()
  const { data: categories = [] } = useCategories()
  const { data: departments = [] } = useDepartments()

  const [tagInput, setTagInput] = useState('')
  const [tagSuggestions, setTagSuggestions] = useState<TagEntry[]>([])
  const [showSuggestions, setShowSuggestions] = useState(false)
  const [deptOpen, setDeptOpen] = useState(false)
  const [catOpen, setCatOpen] = useState(false)
  const deptRef = useRef<HTMLDivElement>(null)
  const catRef = useRef<HTMLDivElement>(null)

  const erpConnected = capabilities?.erpConnected ?? false
  const showDepartment = erpConnected && departments.length > 0
  const hasCategories = categories.length > 0

  // Tag autocomplete
  useEffect(() => {
    if (!token || tagInput.trim().length < 1) {
      setTagSuggestions([])
      return
    }
    platformApi.tags(token, tagInput.trim())
      .then(setTagSuggestions)
      .catch(() => setTagSuggestions([]))
  }, [tagInput, token])

  // Close dropdowns on outside click
  useEffect(() => {
    function onOutside(e: MouseEvent) {
      if (deptRef.current && !deptRef.current.contains(e.target as Node)) setDeptOpen(false)
      if (catRef.current && !catRef.current.contains(e.target as Node)) setCatOpen(false)
    }
    document.addEventListener('mousedown', onOutside)
    return () => document.removeEventListener('mousedown', onOutside)
  }, [])

  const addTag = (tag: string) => {
    const normalized = tag.toLowerCase().trim()
    if (!normalized || value.tags.includes(normalized)) return
    onChange({ ...value, tags: [...value.tags, normalized] })
    setTagInput('')
    setTagSuggestions([])
    setShowSuggestions(false)
  }

  const removeTag = (tag: string) => {
    onChange({ ...value, tags: value.tags.filter((t) => t !== tag) })
  }

  const handleTagKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault()
      addTag(tagInput)
    }
  }

  const selectedDept = value.departmentCode
    ? departments.find((d) => d.code === value.departmentCode)
    : undefined

  const selectedCat = value.categoryCode
    ? categories.find((c) => c.code === value.categoryCode)
    : undefined

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '14px', padding: '10px 12px 12px', fontFamily: 'var(--panel-font)', fontSize: 'var(--panel-fs)' }}>
      {/* ── Department ── */}
      {showDepartment && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <label
            style={{ fontSize: 'var(--panel-fs-label)', color: 'var(--panel-label-color)', fontWeight: 'var(--panel-label-weight)' as React.CSSProperties['fontWeight'] }}
          >
            Department
          </label>
          <div ref={deptRef} style={{ position: 'relative' }}>
            <div style={{ cursor: 'pointer' }} onClick={() => setDeptOpen((o) => !o)}>
              <MetaRow bound={!!selectedDept}>
                {selectedDept ? (
                  <FeelChip
                    label={selectedDept.displayName}
                    kind="dept"
                    onRemove={() => {
                      onChange({ ...value, departmentCode: undefined })
                      setDeptOpen(false)
                    }}
                  />
                ) : (
                  <span style={{ color: '#a8b9c4', fontSize: '12px' }}>
                    All departments
                  </span>
                )}
                <ChevronDown
                  size={13}
                  style={{ color: '#a8b9c4', flexShrink: 0 }}
                  aria-label="Toggle department picker"
                />
              </MetaRow>
            </div>
            {deptOpen && (
              <div
                style={{
                  position: 'absolute',
                  top: 'calc(100% + 4px)',
                  left: 0,
                  right: 0,
                  zIndex: 20,
                  background: '#fff',
                  border: '1px solid #e2eaee',
                  borderRadius: '6px',
                  boxShadow: '0 2px 8px rgba(15,30,42,0.06)',
                  maxHeight: '180px',
                  overflowY: 'auto',
                }}
                role="listbox"
                aria-label="Select department"
              >
                <button
                  type="button"
                  role="option"
                  aria-selected={!selectedDept}
                  style={{
                    width: '100%',
                    textAlign: 'left',
                    padding: '7px 10px',
                    fontSize: '12px',
                    border: 'none',
                    background: !selectedDept ? '#e6f1fb' : 'transparent',
                    color: !selectedDept ? '#0c447c' : '#0f1e2a',
                    cursor: 'pointer',
                  }}
                  onClick={() => {
                    onChange({ ...value, departmentCode: undefined })
                    setDeptOpen(false)
                  }}
                >
                  All departments (empty)
                </button>
                {departments.map((d) => (
                  <button
                    key={d.code}
                    type="button"
                    role="option"
                    aria-selected={value.departmentCode === d.code}
                    style={{
                      width: '100%',
                      textAlign: 'left',
                      padding: '7px 10px',
                      fontSize: '12px',
                      border: 'none',
                      borderTop: '1px solid #eef2f5',
                      background: value.departmentCode === d.code ? '#e6f1fb' : 'transparent',
                      color: value.departmentCode === d.code ? '#0c447c' : '#0f1e2a',
                      cursor: 'pointer',
                    }}
                    onClick={() => {
                      onChange({ ...value, departmentCode: d.code })
                      setDeptOpen(false)
                    }}
                  >
                    {d.displayName}
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {/* ── Category ── */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
        <label style={{ fontSize: 'var(--panel-fs-label)', color: 'var(--panel-label-color)', fontWeight: 'var(--panel-label-weight)' as React.CSSProperties['fontWeight'] }}>
          Category
        </label>
        {!hasCategories ? (
          <Note variant="muted">
            No categories configured.{' '}
            <a
              href="/admin/tenant/categories"
              style={{ color: '#149ba5', textDecoration: 'underline' }}
            >
              configure →
            </a>
          </Note>
        ) : (
          <div ref={catRef} style={{ position: 'relative' }}>
            <div style={{ cursor: 'pointer' }} onClick={() => setCatOpen((o) => !o)}>
              <MetaRow bound={!!selectedCat}>
                {selectedCat ? (
                  <FeelChip
                    label={selectedCat.displayName}
                    kind="cat"
                    onRemove={() => {
                      onChange({ ...value, categoryCode: undefined })
                      setCatOpen(false)
                    }}
                  />
                ) : (
                  <span style={{ color: '#a8b9c4', fontSize: '12px' }}>
                    Uncategorized
                  </span>
                )}
                <ChevronDown
                  size={13}
                  style={{ color: '#a8b9c4', flexShrink: 0 }}
                  aria-label="Toggle category picker"
                />
              </MetaRow>
            </div>
            {catOpen && (
              <div
                style={{
                  position: 'absolute',
                  top: 'calc(100% + 4px)',
                  left: 0,
                  right: 0,
                  zIndex: 20,
                  background: '#fff',
                  border: '1px solid #e2eaee',
                  borderRadius: '6px',
                  boxShadow: '0 2px 8px rgba(15,30,42,0.06)',
                  maxHeight: '180px',
                  overflowY: 'auto',
                }}
                role="listbox"
                aria-label="Select category"
              >
                <button
                  type="button"
                  role="option"
                  aria-selected={!selectedCat}
                  style={{
                    width: '100%',
                    textAlign: 'left',
                    padding: '7px 10px',
                    fontSize: '12px',
                    border: 'none',
                    background: !selectedCat ? '#e1f5ee' : 'transparent',
                    color: !selectedCat ? '#085041' : '#0f1e2a',
                    cursor: 'pointer',
                  }}
                  onClick={() => {
                    onChange({ ...value, categoryCode: undefined })
                    setCatOpen(false)
                  }}
                >
                  Uncategorized
                </button>
                {categories.map((c) => (
                  <button
                    key={c.id}
                    type="button"
                    role="option"
                    aria-selected={value.categoryCode === c.code}
                    style={{
                      width: '100%',
                      textAlign: 'left',
                      padding: '7px 10px',
                      fontSize: '12px',
                      border: 'none',
                      borderTop: '1px solid #eef2f5',
                      background: value.categoryCode === c.code ? '#e1f5ee' : 'transparent',
                      color: value.categoryCode === c.code ? '#085041' : '#0f1e2a',
                      cursor: 'pointer',
                    }}
                    onClick={() => {
                      onChange({ ...value, categoryCode: c.code })
                      setCatOpen(false)
                    }}
                  >
                    {c.displayName}
                  </button>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {/* ── Inactive fields note ── */}
      {!showDepartment && !hasCategories && (
        <Note variant="muted">
          Department and category fields activate once ERP is connected and categories are configured in{' '}
          <a href="/admin/tenant/categories" style={{ color: '#149ba5', textDecoration: 'underline' }}>
            Tenant Admin
          </a>
          . Tags are available now.
        </Note>
      )}

      {/* ── Tags ── */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
        <label style={{ fontSize: 'var(--panel-fs-label)', color: 'var(--panel-label-color)', fontWeight: 'var(--panel-label-weight)' as React.CSSProperties['fontWeight'] }}>
          Tags
        </label>
        <MetaRow>
          {value.tags.map((tag) => (
            <FeelChip
              key={tag}
              label={`#${tag}`}
              kind="tag"
              onRemove={() => removeTag(tag)}
            />
          ))}
          <div style={{ position: 'relative', flex: 1, minWidth: '80px' }}>
            <input
              type="text"
              placeholder={value.tags.length === 0 ? 'add tag…' : '+'}
              value={tagInput}
              onChange={(e) => {
                setTagInput(e.target.value)
                setShowSuggestions(true)
              }}
              onKeyDown={handleTagKeyDown}
              onBlur={() => setTimeout(() => setShowSuggestions(false), 150)}
              aria-label="Add tag"
              style={{
                border: 'none',
                outline: 'none',
                background: 'transparent',
                fontSize: '11px',
                color: '#0f1e2a',
                width: '100%',
                padding: '1px 2px',
                fontFamily: 'inherit',
              }}
            />
            {showSuggestions && tagSuggestions.length > 0 && (
              <div
                style={{
                  position: 'absolute',
                  top: 'calc(100% + 4px)',
                  left: 0,
                  right: 0,
                  zIndex: 20,
                  background: '#fff',
                  border: '1px solid #e2eaee',
                  borderRadius: '6px',
                  boxShadow: '0 2px 8px rgba(15,30,42,0.06)',
                  overflow: 'hidden',
                  minWidth: '160px',
                }}
                role="listbox"
                aria-label="Tag suggestions"
              >
                {tagSuggestions.slice(0, 8).map((s) => (
                  <button
                    key={s.tag}
                    type="button"
                    role="option"
                    aria-selected={false}
                    onMouseDown={() => addTag(s.tag)}
                    style={{
                      width: '100%',
                      textAlign: 'left',
                      padding: '6px 10px',
                      fontSize: '11px',
                      border: 'none',
                      borderTop: '1px solid #eef2f5',
                      background: 'transparent',
                      cursor: 'pointer',
                      display: 'flex',
                      justifyContent: 'space-between',
                      color: '#0f1e2a',
                      fontFamily: "'JetBrains Mono', monospace",
                    }}
                  >
                    <span>{s.tag}</span>
                    <span style={{ color: '#6b7e8c', fontSize: '10px' }}>{s.usageCount}</span>
                  </button>
                ))}
              </div>
            )}
          </div>
        </MetaRow>
      </div>
    </div>
  )
}

export default ArtifactMetadataPanel
