'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { useAuthorization } from '@/lib/auth/use-authorization'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Info } from 'lucide-react'
import { toast } from 'sonner'
import { platformApi } from '@/lib/platform/api'
import type { VisibilityPolicyEntry } from '@/lib/platform/types'

export default function VisibilityPolicyPage() {
  const { data: session, status } = useSession()
  const { hasAnyRole } = useAuthorization()
  const token = (session?.accessToken as string) ?? ''
  const qc = useQueryClient()

  const { data: policy, isLoading } = useQuery<VisibilityPolicyEntry>({
    queryKey: ['pss', 'visibilityPolicy'],
    queryFn: () => platformApi.visibilityPolicy(token),
    enabled: status === 'authenticated',
    staleTime: 300_000,
  })

  const updateScopeMutation = useMutation({
    mutationFn: async (newScope: 'OWN_DEPT' | 'ALL_DEPTS') => {
      const res = await fetch('/api/proxy/admin/config/vars', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ varKey: 'managerScope', varValue: newScope, varType: 'POLICY' }),
      })
      if (!res.ok) throw new Error('Failed to update visibility policy')
      return res.json()
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['pss', 'visibilityPolicy'] })
      qc.invalidateQueries({ queryKey: ['pss', 'capabilities'] })
      toast.success('Visibility policy updated')
    },
    onError: (e: Error) => toast.error(e.message),
  })

  // Update manager-tier flag on a role-group mapping
  const updateManagerTierMutation = useMutation({
    mutationFn: async ({ id, isManagerTier }: { id: string; isManagerTier: boolean }) => {
      const res = await fetch(`/api/proxy/admin/config/role-mappings/${id}/manager-tier`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ isManagerTier }),
      })
      if (!res.ok) throw new Error('Failed to update manager tier flag')
      return res.json()
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['pss', 'visibilityPolicy'] })
      qc.invalidateQueries({ queryKey: ['pss', 'candidateGroups'] })
      toast.success('Manager tier flag updated')
    },
    onError: (e: Error) => toast.error(e.message),
  })

  if (!hasAnyRole(['ADMIN', 'SUPER_ADMIN'])) {
    return <div className="p-6 text-muted-foreground">Access denied.</div>
  }

  return (
    <div className="p-6 space-y-6 max-w-2xl">
      <div>
        <h1 className="text-xl font-semibold">Visibility Policy</h1>
        <p className="text-sm text-muted-foreground mt-1">
          Control which artifacts users can browse based on department scoping.
        </p>
      </div>

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading...</p>
      ) : (
        <>
          <div className="border rounded-md p-4 space-y-3">
            <div className="flex items-start gap-2">
              <div className="flex-1">
                <Label className="text-sm font-medium">Manager Scope</Label>
                <p className="text-xs text-muted-foreground mt-1 flex items-center gap-1">
                  <Info className="h-3 w-3" />
                  When enabled, managers see only tasks within their own department.
                </p>
              </div>
            </div>

            <div className="flex gap-3">
              <Button
                size="sm"
                variant={policy?.managerScope === 'OWN_DEPT' ? 'default' : 'outline'}
                onClick={() => updateScopeMutation.mutate('OWN_DEPT')}
                disabled={updateScopeMutation.isPending}
              >
                Own Department Only
              </Button>
              <Button
                size="sm"
                variant={policy?.managerScope === 'ALL_DEPTS' ? 'default' : 'outline'}
                onClick={() => updateScopeMutation.mutate('ALL_DEPTS')}
                disabled={updateScopeMutation.isPending}
              >
                All Departments
              </Button>
            </div>

            <p className="text-xs text-muted-foreground bg-muted rounded p-2">
              <strong>Own Department Only (default)</strong> — managers see only artifacts from their own department.
              Best for organizations where strict department boundaries apply.
              <br />
              <strong>All Departments</strong> — managers see artifacts from all departments. Best for smaller
              organizations or where cross-department oversight is the norm.
            </p>
          </div>

          {policy && policy.managerTierGroups.length > 0 && (
            <div className="border rounded-md p-4 space-y-2">
              <Label className="text-sm font-medium">Manager-Tier Groups</Label>
              <p className="text-xs text-muted-foreground">
                These Tier 2 groups trigger the manager visibility scope when a user belongs to them.
              </p>
              <ul className="text-sm space-y-1">
                {policy.managerTierGroups.map((g) => (
                  <li key={g} className="flex items-center gap-2">
                    <span className="font-mono bg-muted rounded px-2 py-0.5 text-xs">{g}</span>
                    <span className="text-muted-foreground text-xs">manager-tier</span>
                  </li>
                ))}
              </ul>
              <p className="text-xs text-muted-foreground">
                Manage manager-tier flags in the{' '}
                <a href="/admin/tenant/role-mappings" className="underline text-primary">
                  Role Mappings
                </a>{' '}
                page.
              </p>
            </div>
          )}
        </>
      )}
    </div>
  )
}
