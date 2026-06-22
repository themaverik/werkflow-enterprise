'use client'

import { useState, useMemo, useEffect } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import Link from 'next/link'
import { useSession } from 'next-auth/react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Check, Download, ExternalLink, Info, RefreshCw, ShieldCheck } from 'lucide-react'
import { createConnector, listConnectors } from '@/lib/api/connectors'
import {
  listCredentials,
  getCredentialType,
  AUTH_SCHEME_TO_CREDENTIAL_TYPE,
  type TenantCredentialResponse,
} from '@/lib/api/credentials'
import { PageSurface } from '@/components/layout/page-surface'
import {
  MARKETPLACE_CATALOG,
  type MarketplaceConnector,
  transportLabel,
  authLabel,
  authTypeToScheme,
  buildInstallPayload,
} from '@/lib/marketplace/catalog'
// ─── Install modal ────────────────────────────────────────────────────────────

interface InstallModalProps {
  connector: MarketplaceConnector | null
  open: boolean
  onOpenChange: (open: boolean) => void
  onInstalled: () => void
}

function InstallModal({ connector, open, onOpenChange, onInstalled }: InstallModalProps) {
  const queryClient = useQueryClient()
  const [baseUrl, setBaseUrl] = useState('')
  const [credentialRef, setCredentialRef] = useState('')
  const [credentials, setCredentials] = useState<TenantCredentialResponse[]>([])
  const [credentialsLoading, setCredentialsLoading] = useState(true)
  const [environment, setEnvironment] = useState<'development' | 'staging' | 'production'>(
    'development'
  )
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const authScheme = connector ? authTypeToScheme(connector.primaryAuth) : 'NONE'
  const credentialType = AUTH_SCHEME_TO_CREDENTIAL_TYPE[authScheme]
  // A credential is needed when the connector authenticates with a supported scheme.
  // Unsupported schemes (oauth2, mtls) map to no credential type and cannot be installed here.
  const needsCredential = authScheme !== 'NONE'
  const unsupportedAuth = needsCredential && credentialType === undefined

  useEffect(() => {
    if (!open) return
    setCredentialsLoading(true)
    setCredentials([])
    listCredentials()
      .then(setCredentials)
      .catch((err) => {
        console.error('Failed to load credentials for marketplace install', err)
        setCredentials([])
      })
      .finally(() => setCredentialsLoading(false))
  }, [open])

  const matchingCredentials = useMemo(
    () => credentials.filter((c) => c.credentialType === credentialType),
    [credentials, credentialType],
  )

  const handleInstall = async () => {
    if (!connector) return
    if (!baseUrl.trim()) {
      setError('Base URL is required.')
      return
    }
    if (needsCredential && !credentialRef) {
      setError('Select a credential to install this connector.')
      return
    }

    setLoading(true)
    setError(null)

    try {
      const payload = buildInstallPayload(connector, {
        baseUrl: baseUrl.trim(),
        credentialRef: needsCredential ? credentialRef : undefined,
        environment,
      })
      await createConnector({ ...payload, active: true })
      queryClient.invalidateQueries({ queryKey: ['marketplace.installed'] })
      onInstalled()
      onOpenChange(false)
      resetForm()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Install failed. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  const resetForm = () => {
    setBaseUrl('')
    setCredentialRef('')
    setEnvironment('development')
    setError(null)
  }

  const handleOpenChange = (open: boolean) => {
    if (!open) resetForm()
    onOpenChange(open)
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>Install {connector?.displayName}</DialogTitle>
          <DialogDescription>
            Register this connector for your tenant. Supply the runtime endpoint and select
            an OpenBao-backed credential — secret material is never returned by the API.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 pt-2">
          <div className="space-y-1.5">
            <Label htmlFor="marketplace-baseUrl">Base URL</Label>
            <Input
              id="marketplace-baseUrl"
              placeholder="https://api.example.com"
              value={baseUrl}
              onChange={(e) => setBaseUrl(e.target.value)}
              autoComplete="off"
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="marketplace-environment">Environment</Label>
            <Select
              value={environment}
              onValueChange={(v) =>
                setEnvironment(v as 'development' | 'staging' | 'production')
              }
            >
              <SelectTrigger id="marketplace-environment">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="development">Development</SelectItem>
                <SelectItem value="staging">Staging</SelectItem>
                <SelectItem value="production">Production</SelectItem>
              </SelectContent>
            </Select>
          </div>

          {needsCredential && unsupportedAuth && (
            <p className="text-sm text-muted-foreground">
              {authLabel(connector?.primaryAuth ?? 'bearer')} authentication is not yet supported
              for one-click install. Configure this connector manually from the Connectors page.
            </p>
          )}

          {needsCredential && !unsupportedAuth && (
            <div className="space-y-1.5">
              <Label htmlFor="marketplace-credential">
                {authLabel(connector?.primaryAuth ?? 'bearer')} credential
              </Label>
              {credentialsLoading ? (
                <p className="text-sm text-muted-foreground">Loading credentials…</p>
              ) : matchingCredentials.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  No {getCredentialType(credentialType)?.displayName ?? credentialType} credentials found.{' '}
                  <Link href="/admin/tenant/credentials" className="underline underline-offset-2">
                    Create one first
                  </Link>
                </p>
              ) : (
                <Select value={credentialRef} onValueChange={setCredentialRef}>
                  <SelectTrigger id="marketplace-credential" className="font-mono text-sm">
                    <SelectValue placeholder="Select credential…" />
                  </SelectTrigger>
                  <SelectContent>
                    {matchingCredentials.map((c) => (
                      <SelectItem key={c.id} value={c.label} className="font-mono text-xs">
                        {c.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
              <p className="text-xs text-muted-foreground flex items-center gap-1">
                <ShieldCheck className="h-3 w-3 shrink-0" />
                Secret material is stored in OpenBao. Never returned by the API.
              </p>
            </div>
          )}

          {error && (
            <p className="text-sm text-destructive">{error}</p>
          )}

          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={() => handleOpenChange(false)}>
              Cancel
            </Button>
            <Button onClick={handleInstall} disabled={loading || unsupportedAuth}>
              {loading ? <RefreshCw className="h-4 w-4 animate-spin mr-2" /> : null}
              Install
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}

// ─── Connector card ───────────────────────────────────────────────────────────

interface ConnectorCardProps {
  connector: MarketplaceConnector
  installed: boolean
  onInstall: (connector: MarketplaceConnector) => void
}

function ConnectorCard({ connector, installed, onInstall }: ConnectorCardProps) {
  const isOfficial = connector.source === 'official'
  const visibleTags = connector.tags.slice(0, 5)
  const isRestTransport = connector.transport === 'rest'
  const isDatabaseTransport = connector.transport === 'database'

  return (
    <article className="group relative flex flex-col rounded-[14px] border border-slate-200 bg-white p-[22px_22px_18px] wf-card-interactive">
      {/* Official accent bar */}
      {isOfficial && (
        <span className="absolute left-0 top-[18px] bottom-[18px] w-[3px] rounded-r-[3px] bg-gradient-to-b from-[#2EC4A0] to-[#149ba5]" />
      )}

      {/* Card head */}
      <div className="flex items-start gap-[14px] mb-[14px]">
        {/* Logo tile */}
        <div
          className={[
            'w-[50px] h-[50px] rounded-xl border flex items-center justify-center flex-shrink-0 shadow-[0_1px_2px_rgba(15,30,42,0.05)]',
            isOfficial
              ? 'bg-[#0c1925] border-[#0c1925]'
              : 'bg-white border-slate-200',
          ].join(' ')}
        >
          {connector.logoPath ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={connector.logoPath}
              alt={connector.displayName}
              className="w-[30px] h-[30px] object-contain"
            />
          ) : (
            <div className="w-[30px] h-[30px] flex items-center justify-center text-[14px] font-bold text-slate-400">
              {connector.displayName.charAt(0)}
            </div>
          )}
        </div>

        {/* Card identity */}
        <div className="flex-1 min-w-0">
          {/* Name row */}
          <div className="flex items-center gap-2 flex-wrap">
            <h3 className="text-[16.5px] font-bold text-[#0f1e2a] tracking-[-0.01em]">
              {connector.displayName}
            </h3>
            {isOfficial && (
              <span className="text-[10.5px] font-bold tracking-[0.02em] uppercase px-2 py-0.5 rounded-full bg-[#149ba5] text-white">
                Official
              </span>
            )}
            {installed && (
              <span className="text-[10.5px] font-bold tracking-[0.02em] uppercase px-2 py-0.5 rounded-full bg-slate-100 text-slate-500 flex items-center gap-1">
                <Check className="h-3 w-3" />
                Installed
              </span>
            )}
          </div>
          {/* Vendor row */}
          <p className="text-[12.5px] text-[#65798a] mt-[3px] flex items-center gap-[6px]">
            <span className="font-mono text-[11.5px] text-[#33495a]">v{connector.version}</span>
            &middot;
            {connector.vendor}
          </p>
        </div>
      </div>

      {/* Description */}
      <p className="text-[13.5px] text-[#33495a] leading-[1.55] mb-4 flex-1">
        {connector.description}
      </p>

      {/* Spec chips */}
      <div className="flex flex-wrap gap-[7px] mb-[13px]">
        {/* Transport chip */}
        <span className="inline-flex items-center gap-[5px] text-[11.5px] font-semibold px-[9px] py-[4px] rounded-[7px] bg-[#f0fafb] text-[#0e7a83] border border-[#dff4f5]">
          {isDatabaseTransport ? (
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <ellipse cx="12" cy="5" rx="8" ry="3" />
              <path d="M4 5v14c0 1.7 3.6 3 8 3s8-1.3 8-3V5" />
            </svg>
          ) : isRestTransport ? (
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M4 6h16M4 12h16M4 18h10" />
            </svg>
          ) : (
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M4 6h16M4 12h16M4 18h10" />
            </svg>
          )}
          {transportLabel(connector.transport)}
        </span>

        {/* Auth chip */}
        <span className="inline-flex items-center gap-[5px] text-[11.5px] font-semibold px-[9px] py-[4px] rounded-[7px] bg-white text-[#33495a] border border-slate-200">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M21 2l-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0 3 3L22 7l-3-3m-3.5 3.5L19 4" />
          </svg>
          {authLabel(connector.primaryAuth)}
        </span>

        {/* Ops chip */}
        <span className="inline-flex items-center gap-[5px] text-[11px] font-semibold font-mono px-[9px] py-[4px] rounded-[7px] bg-[#f3f6f8] text-[#65798a] border border-[#eef3f5]">
          {connector.operationCount} op{connector.operationCount !== 1 ? 's' : ''}
        </span>
      </div>

      {/* Tags */}
      <div className="flex flex-wrap gap-[6px] pb-4 mb-4 border-b border-slate-100">
        {visibleTags.map((tag) => (
          <span
            key={tag}
            className="text-[11.5px] text-[#65798a] bg-[#f4f7f8] px-[9px] py-[3px] rounded-[6px] font-medium"
          >
            #{tag}
          </span>
        ))}
      </div>

      {/* Card footer */}
      <div className="flex items-center justify-between">
        {connector.documentationUrl ? (
          <a
            href={connector.documentationUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-[6px] text-[13px] font-semibold text-[#65798a] hover:text-[#0e7a83] transition-colors no-underline"
          >
            <ExternalLink width={14} height={14} />
            Docs
          </a>
        ) : (
          <span />
        )}

        {installed ? (
          <Button
            size="sm"
            variant="outline"
            disabled
            className="h-[38px] px-[18px] text-[13.5px] font-semibold gap-[7px]"
          >
            <Check className="h-4 w-4" />
            Installed
          </Button>
        ) : (
          <button
            onClick={() => onInstall(connector)}
            className="inline-flex items-center gap-[7px] h-[38px] px-[18px] border-0 rounded-[10px] bg-[#149ba5] text-white text-[13.5px] font-semibold cursor-pointer shadow-[0_2px_8px_-2px_rgba(20,155,165,0.5)] hover:bg-[#0e7a83] transition-colors"
          >
            <Download className="h-4 w-4" />
            Install
          </button>
        )}
      </div>
    </article>
  )
}

// ─── Page ─────────────────────────────────────────────────────────────────────

export default function MarketplacePage() {
  const { status } = useSession()
  const [installing, setInstalling] = useState<MarketplaceConnector | null>(null)

  const { data: installedConnectors = [] } = useQuery({
    queryKey: ['marketplace.installed'],
    queryFn: listConnectors,
    enabled: status === 'authenticated',
  })

  const installedKeys = useMemo(
    () => new Set(installedConnectors.map((c) => c.connectorKey)),
    [installedConnectors],
  )

  if (status === 'loading') {
    return (
      <div className="flex items-center justify-center py-24">
        <RefreshCw className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    )
  }

  const official = MARKETPLACE_CATALOG.filter((c) => c.source === 'official')
  const community = MARKETPLACE_CATALOG.filter((c) => c.source === 'community')

  return (
    <PageSurface>
      <div className="space-y-8">
        {/* Header */}
        <div>
          <h1 className="text-[30px] font-bold tracking-[-0.015em] text-[#0f1e2a]">Marketplace</h1>
          <p className="text-[15px] text-[#65798a] mt-[6px] max-w-[680px]">
            Browse and install connector definitions maintained by the Werkflow core team and community.
          </p>
        </div>

        {/* Info banner */}
        <div className="flex gap-[13px] items-start p-[14px_18px] rounded-xl bg-[#fff8ec] border border-[#f4e2bf] mb-[34px]">
          <span className="w-[30px] h-[30px] rounded-lg bg-white border border-[#f4e2bf] flex items-center justify-center text-[#b06a00] flex-shrink-0">
            <Info className="h-4 w-4" />
          </span>
          <p className="text-[13px] text-[#6b5325] leading-[1.55]">
            <strong className="text-[#5a3d08]">Catalog is curated for this release.</strong>{' '}
            Connector definitions below reflect the current bundled list; refreshing will not pull
            updates. Community contributions via GitHub PRs are planned for a future release.
          </p>
        </div>

        {/* Official connectors */}
        <section className="space-y-4">
          <div>
            <div className="flex items-baseline gap-3 mb-1">
              <h2 className="text-[19px] font-bold tracking-[-0.01em] text-[#0f1e2a]">
                Official Connectors
              </h2>
              <span className="font-mono text-[11px] font-semibold text-[#65798a] bg-white border border-slate-200 px-2 py-0.5 rounded-full">
                {official.length}
              </span>
            </div>
            <p className="text-sm text-muted-foreground">
              Maintained by the Werkflow core team. Guaranteed to be compatible with the current
              platform version.
            </p>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {official.map((connector) => (
              <ConnectorCard
                key={connector.key}
                connector={connector}
                installed={installedKeys.has(connector.key)}
                onInstall={setInstalling}
              />
            ))}
          </div>
        </section>

        {/* Community connectors */}
        <section className="space-y-4">
          <div>
            <div className="flex items-baseline gap-3 mb-1">
              <h2 className="text-[19px] font-bold tracking-[-0.01em] text-[#0f1e2a]">
                Community Connectors
              </h2>
              <span className="font-mono text-[11px] font-semibold text-[#65798a] bg-white border border-slate-200 px-2 py-0.5 rounded-full">
                {community.length}
              </span>
            </div>
            <p className="text-sm text-muted-foreground">
              Contributed by the open-source community. Review the connector definition before
              installing in a production environment.
            </p>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {community.map((connector) => (
              <ConnectorCard
                key={connector.key}
                connector={connector}
                installed={installedKeys.has(connector.key)}
                onInstall={setInstalling}
              />
            ))}
          </div>
        </section>

        {/* Install modal */}
        <InstallModal
          connector={installing}
          open={installing !== null}
          onOpenChange={(open) => {
            if (!open) setInstalling(null)
          }}
          onInstalled={() => setInstalling(null)}
        />
      </div>
    </PageSurface>
  )
}
