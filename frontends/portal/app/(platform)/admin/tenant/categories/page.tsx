'use client'

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { useAuthorization } from '@/lib/auth/use-authorization'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { toast } from 'sonner'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { platformApi } from '@/lib/platform/api'
import type { CategoryEntry, CategoryRequest } from '@/lib/platform/types'
import { PageSurface } from '@/components/layout/page-surface'

const COLOR_OPTIONS = ['purple', 'green', 'blue', 'cyan', 'orange', 'amber', 'gray', 'red', 'pink']

function ColorSwatch({ color }: { color: string | null }) {
  const colorMap: Record<string, string> = {
    purple: 'bg-purple-400', green: 'bg-green-400', blue: 'bg-blue-400',
    cyan: 'bg-cyan-400', orange: 'bg-orange-400', amber: 'bg-amber-400',
    gray: 'bg-gray-400', red: 'bg-red-400', pink: 'bg-pink-400',
  }
  return (
    <span className={`inline-block w-4 h-4 rounded-full ${colorMap[color ?? ''] ?? 'bg-gray-200'}`} />
  )
}

interface FormState {
  displayName: string
  code: string
  icon: string
  color: string
  displayOrder: string
}

const EMPTY_FORM: FormState = { displayName: '', code: '', icon: '', color: 'blue', displayOrder: '0' }

export default function CategoriesPage() {
  const { data: session, status } = useSession()
  const { hasAnyRole } = useAuthorization()
  const token = (session?.accessToken as string) ?? ''
  const qc = useQueryClient()

  const [dialogOpen, setDialogOpen] = useState(false)
  const [editTarget, setEditTarget] = useState<CategoryEntry | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY_FORM)

  const { data: categories = [], isLoading } = useQuery<CategoryEntry[]>({
    queryKey: ['pss', 'categories'],
    queryFn: () => platformApi.categories(token),
    enabled: status === 'authenticated',
    staleTime: 300_000,
  })

  const createMutation = useMutation({
    mutationFn: (body: CategoryRequest) => platformApi.createCategory(token, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['pss', 'categories'] })
      toast.success('Category created')
      setDialogOpen(false)
      setForm(EMPTY_FORM)
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: CategoryRequest }) =>
      platformApi.updateCategory(token, id, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['pss', 'categories'] })
      toast.success('Category updated')
      setDialogOpen(false)
      setEditTarget(null)
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => platformApi.deleteCategory(token, id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['pss', 'categories'] })
      toast.success('Category deleted')
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const openCreate = () => {
    setEditTarget(null)
    setForm(EMPTY_FORM)
    setDialogOpen(true)
  }

  const openEdit = (cat: CategoryEntry) => {
    setEditTarget(cat)
    setForm({
      displayName: cat.displayName,
      code: cat.code,
      icon: cat.icon ?? '',
      color: cat.color ?? 'blue',
      displayOrder: String(cat.displayOrder),
    })
    setDialogOpen(true)
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    const body: CategoryRequest = {
      displayName: form.displayName.trim(),
      code: form.code.trim(),
      icon: form.icon.trim() || undefined,
      color: form.color || undefined,
      displayOrder: parseInt(form.displayOrder, 10) || 0,
    }
    if (editTarget) {
      updateMutation.mutate({ id: editTarget.id, body })
    } else {
      createMutation.mutate(body)
    }
  }

  if (!hasAnyRole(['ADMIN', 'SUPER_ADMIN'])) {
    return <div className="p-6 text-muted-foreground">Access denied.</div>
  }

  return (
    <PageSurface>
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold">Categories</h1>
          <p className="text-sm text-muted-foreground mt-1">
            Tenant-registered categories for artifact catalog grouping.
          </p>
        </div>
        <Button size="sm" onClick={openCreate}>
          <Plus className="h-4 w-4 mr-1" /> New Category
        </Button>
      </div>

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading...</p>
      ) : categories.length === 0 ? (
        <p className="text-sm text-muted-foreground">No categories yet. Create one to start organizing your catalog.</p>
      ) : (
        <div className="border rounded-md overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-muted text-left">
              <tr>
                <th className="px-4 py-2 font-medium">Name</th>
                <th className="px-4 py-2 font-medium">Code</th>
                <th className="px-4 py-2 font-medium">Color</th>
                <th className="px-4 py-2 font-medium">Order</th>
                <th className="px-4 py-2 font-medium">Actions</th>
              </tr>
            </thead>
            <tbody>
              {categories.map((cat) => (
                <tr key={cat.id} className="border-t hover:bg-muted/50">
                  <td className="px-4 py-2">{cat.displayName}</td>
                  <td className="px-4 py-2 font-mono text-xs">{cat.code}</td>
                  <td className="px-4 py-2">
                    <ColorSwatch color={cat.color} />
                    <span className="ml-2 text-muted-foreground">{cat.color}</span>
                  </td>
                  <td className="px-4 py-2">{cat.displayOrder}</td>
                  <td className="px-4 py-2 flex gap-2">
                    <Button variant="ghost" size="sm" onClick={() => openEdit(cat)}>
                      <Pencil className="h-3 w-3" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => deleteMutation.mutate(cat.id)}
                      disabled={deleteMutation.isPending}
                    >
                      <Trash2 className="h-3 w-3 text-destructive" />
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editTarget ? 'Edit Category' : 'New Category'}</DialogTitle>
          </DialogHeader>
          <form onSubmit={handleSubmit} className="space-y-4 mt-2">
            <div className="space-y-1">
              <Label>Display Name</Label>
              <Input
                required
                value={form.displayName}
                onChange={(e) => setForm({ ...form, displayName: e.target.value })}
                placeholder="Human Resources"
              />
            </div>
            <div className="space-y-1">
              <Label>Code</Label>
              <Input
                required
                value={form.code}
                onChange={(e) => setForm({ ...form, code: e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, '') })}
                placeholder="hr"
                pattern="^[a-z0-9-]+$"
              />
              <p className="text-xs text-muted-foreground">Lowercase alphanumeric with hyphens only.</p>
            </div>
            <div className="space-y-1">
              <Label>Icon (optional)</Label>
              <Input
                value={form.icon}
                onChange={(e) => setForm({ ...form, icon: e.target.value })}
                placeholder="users"
              />
            </div>
            <div className="space-y-1">
              <Label>Color</Label>
              <div className="flex gap-2 flex-wrap">
                {COLOR_OPTIONS.map((c) => (
                  <button
                    key={c}
                    type="button"
                    onClick={() => setForm({ ...form, color: c })}
                    className={`rounded-full w-6 h-6 border-2 transition-all ${
                      form.color === c ? 'border-primary scale-110' : 'border-transparent'
                    }`}
                  >
                    <ColorSwatch color={c} />
                  </button>
                ))}
              </div>
            </div>
            <div className="space-y-1">
              <Label>Display Order</Label>
              <Input
                type="number"
                value={form.displayOrder}
                onChange={(e) => setForm({ ...form, displayOrder: e.target.value })}
                min="0"
              />
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <Button type="button" variant="outline" onClick={() => setDialogOpen(false)}>Cancel</Button>
              <Button type="submit" disabled={createMutation.isPending || updateMutation.isPending}>
                {editTarget ? 'Save Changes' : 'Create'}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>
    </div>
    </PageSurface>
  )
}
