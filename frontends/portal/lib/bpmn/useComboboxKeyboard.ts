import { useState, useEffect, useCallback } from 'react'
import type { Group, GroupItem } from '@/components/ui/VariableComboBox'

interface UseComboboxKeyboardOptions {
  groups: Group[]
  onCommit: (item: GroupItem) => void
  onRemoveLast: () => void
  onClose: () => void
  query: string
}

interface UseComboboxKeyboardResult {
  focusedId: string | undefined
  handlers: React.KeyboardEventHandler
}

function flatItems(groups: Group[]): GroupItem[] {
  return groups.flatMap((g) => g.items)
}

export function useComboboxKeyboard({
  groups,
  onCommit,
  onRemoveLast,
  onClose,
  query,
}: UseComboboxKeyboardOptions): UseComboboxKeyboardResult {
  const [focusedId, setFocusedId] = useState<string | undefined>(undefined)

  // Reset to first item whenever the filtered group list changes
  useEffect(() => {
    const items = flatItems(groups)
    setFocusedId(items[0]?.id)
  }, [groups])

  const handlers = useCallback<React.KeyboardEventHandler>(
    (e) => {
      const items = flatItems(groups)

      if (e.key === 'ArrowDown') {
        e.preventDefault()
        setFocusedId((prev) => {
          if (!prev) return items[0]?.id
          const idx = items.findIndex((it) => it.id === prev)
          return items[idx + 1]?.id ?? items[0]?.id
        })
        return
      }

      if (e.key === 'ArrowUp') {
        e.preventDefault()
        setFocusedId((prev) => {
          if (!prev) return items[items.length - 1]?.id
          const idx = items.findIndex((it) => it.id === prev)
          return items[idx - 1]?.id ?? items[items.length - 1]?.id
        })
        return
      }

      if (e.key === 'Enter') {
        e.preventDefault()
        const item = items.find((it) => it.id === focusedId)
        if (item) onCommit(item)
        return
      }

      if (e.key === 'Escape') {
        e.preventDefault()
        onClose()
        return
      }

      if (e.key === 'Backspace' && !query) {
        e.preventDefault()
        onRemoveLast()
      }
    },
    [groups, focusedId, query, onCommit, onRemoveLast, onClose]
  )

  return { focusedId, handlers }
}
