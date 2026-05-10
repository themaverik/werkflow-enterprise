'use client'

import { useState } from 'react'
import { useSession } from 'next-auth/react'
import { useRouter } from 'next/navigation'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { FlaskConical, RefreshCw } from 'lucide-react'
import {
  createDatasource,
  updateDatasource,
  testDatasourceConnection,
  type TenantDatasourceRequest,
  type TenantDatasourceResponse,
} from '@/lib/api/datasources'

const DRIVER_OPTIONS = [
  { label: 'PostgreSQL', value: 'org.postgresql.Driver' },
  { label: 'MySQL / MariaDB', value: 'com.mysql.cj.jdbc.Driver' },
  { label: 'Oracle', value: 'oracle.jdbc.OracleDriver' },
  { label: 'Microsoft SQL Server', value: 'com.microsoft.sqlserver.jdbc.SQLServerDriver' },
  { label: 'H2 (dev/test)', value: 'org.h2.Driver' },
]

const DIALECT_OPTIONS = [
  { label: 'PostgreSQL', value: 'postgresql' },
  { label: 'MySQL', value: 'mysql' },
  { label: 'Oracle', value: 'oracle' },
  { label: 'MS SQL Server', value: 'mssql' },
  { label: 'H2', value: 'h2' },
  { label: 'ANSI (generic)', value: 'ansi' },
]

interface Props {
  /** When provided, switches to edit mode. */
  existing?: TenantDatasourceResponse
}

type FormState = {
  ref: string
  jdbcUrl: string
  driverClassName: string
  username: string
  passwordSecretRef: string
  dialect: string
  poolMinSize: string
  poolMaxSize: string
  connectionTimeoutSeconds: string
  idleTimeoutSeconds: string
}

function defaultForm(existing?: TenantDatasourceResponse): FormState {
  return {
    ref: existing?.ref ?? '',
    jdbcUrl: existing?.jdbcUrl ?? '',
    driverClassName: existing?.driverClassName ?? '',
    username: existing?.username ?? '',
    passwordSecretRef: existing?.passwordSecretRef ?? '',
    dialect: existing?.dialect ?? '',
    poolMinSize: String(existing?.poolMinSize ?? 1),
    poolMaxSize: String(existing?.poolMaxSize ?? 5),
    connectionTimeoutSeconds: String(existing?.connectionTimeoutSeconds ?? 5),
    idleTimeoutSeconds: String(existing?.idleTimeoutSeconds ?? 600),
  }
}

export function DatasourceForm({ existing }: Props) {
  const { data: session } = useSession()
  const token = (session?.accessToken as string) ?? ''
  const router = useRouter()

  const [form, setForm] = useState<FormState>(defaultForm(existing))
  const [saving, setSaving] = useState(false)
  const [testing, setTesting] = useState(false)

  const isEdit = !!existing

  function setField(field: keyof FormState, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }))
  }

  function buildRequest(): TenantDatasourceRequest {
    return {
      ref: form.ref.trim(),
      jdbcUrl: form.jdbcUrl.trim(),
      driverClassName: form.driverClassName.trim(),
      username: form.username.trim(),
      passwordSecretRef: form.passwordSecretRef.trim(),
      dialect: form.dialect || undefined,
      poolMinSize: parseInt(form.poolMinSize, 10) || 1,
      poolMaxSize: parseInt(form.poolMaxSize, 10) || 5,
      connectionTimeoutSeconds: parseInt(form.connectionTimeoutSeconds, 10) || 5,
      idleTimeoutSeconds: parseInt(form.idleTimeoutSeconds, 10) || 600,
    }
  }

  async function handleSave(e: React.FormEvent) {
    e.preventDefault()
    setSaving(true)
    try {
      if (isEdit) {
        await updateDatasource(existing.ref, buildRequest(), token)
        toast.success('Datasource updated')
      } else {
        await createDatasource(buildRequest(), token)
        toast.success('Datasource registered')
      }
      router.push('/admin/tenant/datasources')
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Unknown error'
      toast.error(`Failed to ${isEdit ? 'update' : 'create'} datasource: ${msg}`)
    } finally {
      setSaving(false)
    }
  }

  async function handleTest() {
    if (!isEdit) {
      toast.info('Save the datasource first, then test the connection.')
      return
    }
    setTesting(true)
    try {
      const result = await testDatasourceConnection(existing.ref, token)
      if (result.ok) {
        toast.success(`Connected in ${result.latencyMs}ms — ${result.message}`)
      } else {
        toast.error(`Failed: ${result.message}`)
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Unknown error'
      toast.error(`Connection test error: ${msg}`)
    } finally {
      setTesting(false)
    }
  }

  return (
    <form onSubmit={handleSave} className="space-y-5">
      {/* Ref (immutable after creation) */}
      <div className="space-y-1.5">
        <Label htmlFor="ref">
          Reference Slug <span className="text-muted-foreground text-xs">(immutable after creation)</span>
        </Label>
        <Input
          id="ref"
          placeholder="demo-h2-hris"
          value={form.ref}
          onChange={(e) => setField('ref', e.target.value)}
          pattern="^[a-z][a-z0-9-]*$"
          required
          disabled={isEdit}
          className="font-mono"
        />
        <p className="text-xs text-muted-foreground">
          Lowercase letters, digits, hyphens. Must start with a letter.
        </p>
      </div>

      {/* JDBC URL */}
      <div className="space-y-1.5">
        <Label htmlFor="jdbcUrl">JDBC URL</Label>
        <Input
          id="jdbcUrl"
          placeholder="jdbc:postgresql://db-host:5432/mydb"
          value={form.jdbcUrl}
          onChange={(e) => setField('jdbcUrl', e.target.value)}
          required
          className="font-mono text-sm"
        />
      </div>

      {/* Driver class */}
      <div className="space-y-1.5">
        <Label htmlFor="driverClassName">Driver Class</Label>
        <Select
          value={form.driverClassName}
          onValueChange={(v) => setField('driverClassName', v)}
        >
          <SelectTrigger id="driverClassName" className="font-mono text-sm">
            <SelectValue placeholder="Select driver…" />
          </SelectTrigger>
          <SelectContent>
            {DRIVER_OPTIONS.map((d) => (
              <SelectItem key={d.value} value={d.value} className="font-mono text-xs">
                {d.label} — <span className="text-muted-foreground">{d.value}</span>
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* Dialect */}
      <div className="space-y-1.5">
        <Label htmlFor="dialect">Dialect (optional)</Label>
        <Select value={form.dialect} onValueChange={(v) => setField('dialect', v)}>
          <SelectTrigger id="dialect">
            <SelectValue placeholder="Select dialect…" />
          </SelectTrigger>
          <SelectContent>
            {DIALECT_OPTIONS.map((d) => (
              <SelectItem key={d.value} value={d.value}>{d.label}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* Username */}
      <div className="space-y-1.5">
        <Label htmlFor="username">Username</Label>
        <Input
          id="username"
          placeholder="db_user"
          value={form.username}
          onChange={(e) => setField('username', e.target.value)}
          required
          className="font-mono"
        />
      </div>

      {/* Password Secret Ref */}
      <div className="space-y-1.5">
        <Label htmlFor="passwordSecretRef">Password Secret Ref</Label>
        <Input
          id="passwordSecretRef"
          placeholder="werkflow.secrets.db.hris-password"
          value={form.passwordSecretRef}
          onChange={(e) => setField('passwordSecretRef', e.target.value)}
          required
          className="font-mono text-sm"
        />
        <p className="text-xs text-muted-foreground">
          A key into the secrets manager — not the raw password. The engine resolves it at runtime.
        </p>
      </div>

      {/* Pool settings */}
      <fieldset className="border border-border rounded-lg p-4 space-y-4">
        <legend className="text-sm font-medium px-1">Connection Pool</legend>
        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-1.5">
            <Label htmlFor="poolMinSize">Min Size</Label>
            <Input
              id="poolMinSize"
              type="number"
              min={0}
              value={form.poolMinSize}
              onChange={(e) => setField('poolMinSize', e.target.value)}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="poolMaxSize">Max Size</Label>
            <Input
              id="poolMaxSize"
              type="number"
              min={1}
              max={50}
              value={form.poolMaxSize}
              onChange={(e) => setField('poolMaxSize', e.target.value)}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="connectionTimeoutSeconds">Connection Timeout (s)</Label>
            <Input
              id="connectionTimeoutSeconds"
              type="number"
              min={1}
              value={form.connectionTimeoutSeconds}
              onChange={(e) => setField('connectionTimeoutSeconds', e.target.value)}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="idleTimeoutSeconds">Idle Timeout (s)</Label>
            <Input
              id="idleTimeoutSeconds"
              type="number"
              min={1}
              value={form.idleTimeoutSeconds}
              onChange={(e) => setField('idleTimeoutSeconds', e.target.value)}
            />
          </div>
        </div>
      </fieldset>

      {/* Actions */}
      <div className="flex items-center justify-between pt-2">
        <Button
          type="button"
          variant="outline"
          onClick={handleTest}
          disabled={testing || !isEdit}
          title={isEdit ? 'Test live connection' : 'Save datasource first to test connection'}
        >
          {testing ? (
            <RefreshCw className="h-4 w-4 animate-spin mr-2" />
          ) : (
            <FlaskConical className="h-4 w-4 mr-2" />
          )}
          Test Connection
        </Button>

        <div className="flex gap-2">
          <Button
            type="button"
            variant="outline"
            onClick={() => router.push('/admin/tenant/datasources')}
          >
            Cancel
          </Button>
          <Button type="submit" disabled={saving}>
            {saving && <RefreshCw className="h-4 w-4 animate-spin mr-2" />}
            {isEdit ? 'Update' : 'Register'}
          </Button>
        </div>
      </div>
    </form>
  )
}
