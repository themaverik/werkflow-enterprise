'use client'

import { useEffect, useState } from 'react'
import { useSession } from 'next-auth/react'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { usePlatformCapabilities, useCategories, useDepartments } from '@/lib/platform/usePlatformCapabilities'
import { platformApi } from '@/lib/platform/api'
import type { ArtifactMetadata, TagEntry } from '@/lib/platform/types'

interface Props {
  artifactType: 'process' | 'form' | 'dmn'
  value: ArtifactMetadata
  onChange: (v: ArtifactMetadata) => void
}

/**
 * Shared metadata panel used by BPMN, Form, and DMN designers.
 * Provides department (visibility), category (catalog), and tags (search) inputs.
 * Per ADR-010: department is visibility scoping only — not a routing input.
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

  const erpConnected = capabilities?.erpConnected ?? false
  const showDepartment = erpConnected && departments.length > 0

  // Fetch tag autocomplete suggestions
  useEffect(() => {
    if (!token || tagInput.trim().length < 1) {
      setTagSuggestions([])
      return
    }
    platformApi.tags(token, tagInput.trim())
      .then(setTagSuggestions)
      .catch(() => setTagSuggestions([]))
  }, [tagInput, token])

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

  return (
    <div className="space-y-4 p-4 border rounded-md bg-muted/30">
      <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
        Metadata
      </p>

      {showDepartment && (
        <div className="space-y-1">
          <Label className="text-xs">
            Department
            <span className="text-muted-foreground ml-1">(visibility scope)</span>
          </Label>
          <Select
            value={value.departmentCode || '__none__'}
            onValueChange={(v) => onChange({ ...value, departmentCode: v === '__none__' ? undefined : v })}
          >
            <SelectTrigger className="h-8 text-xs">
              <SelectValue placeholder="All departments (visible to all)" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="__none__">All departments</SelectItem>
              {departments.map((d) => (
                <SelectItem key={d.code} value={d.code}>
                  {d.displayName}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <p className="text-xs text-muted-foreground">
            Choose a department to scope visibility, or leave empty for all-departments visibility.
          </p>
        </div>
      )}

      <div className="space-y-1">
        <Label className="text-xs">Category</Label>
        <Select
          value={value.categoryCode || '__none__'}
          onValueChange={(v) => onChange({ ...value, categoryCode: v === '__none__' ? undefined : v })}
        >
          <SelectTrigger className="h-8 text-xs">
            <SelectValue placeholder="Select category" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="__none__">Uncategorized</SelectItem>
            {categories.map((c) => (
              <SelectItem key={c.id} value={c.code}>
                {c.displayName}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="space-y-1">
        <Label className="text-xs">Tags</Label>
        <div className="flex flex-wrap gap-1 mb-1">
          {value.tags.map((tag) => (
            <Badge key={tag} variant="secondary" className="text-xs gap-1">
              {tag}
              <button
                type="button"
                onClick={() => removeTag(tag)}
                className="ml-1 opacity-60 hover:opacity-100 text-xs leading-none"
              >
                ×
              </button>
            </Badge>
          ))}
        </div>
        <div className="relative">
          <Input
            className="h-7 text-xs"
            placeholder="Add tag and press Enter"
            value={tagInput}
            onChange={(e) => {
              setTagInput(e.target.value)
              setShowSuggestions(true)
            }}
            onKeyDown={handleTagKeyDown}
            onBlur={() => setTimeout(() => setShowSuggestions(false), 150)}
          />
          {showSuggestions && tagSuggestions.length > 0 && (
            <div className="absolute z-10 top-full mt-1 w-full border rounded bg-background shadow text-xs">
              {tagSuggestions.slice(0, 8).map((s) => (
                <button
                  key={s.tag}
                  type="button"
                  className="w-full text-left px-2 py-1 hover:bg-muted flex justify-between"
                  onMouseDown={() => addTag(s.tag)}
                >
                  <span>{s.tag}</span>
                  <span className="text-muted-foreground">{s.usageCount}</span>
                </button>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export default ArtifactMetadataPanel
