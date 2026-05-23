'use client'

import { useState } from 'react'
import { useSession } from 'next-auth/react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
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
import { Download, ExternalLink, RefreshCw, ShieldCheck } from 'lucide-react'
import { createConnector } from '@/lib/api/connectors'
import { PageSurface } from '@/components/layout/page-surface'
import {
  MARKETPLACE_CATALOG,
  type MarketplaceConnector,
  transportLabel,
  authLabel,
  buildInstallPayload,
} from '@/lib/marketplace/catalog'

// ─── Transport badge variant ──────────────────────────────────────────────────

function transportBadgeVariant(
  transport: MarketplaceConnector['transport']
): 'default' | 'secondary' | 'outline' | 'warning' {
  if (transport === 'rest') return 'default'
  if (transport === 'database') return 'secondary'
  return 'outline'
}

// ─── Install modal ────────────────────────────────────────────────────────────

interface InstallModalProps {
  connector: MarketplaceConnector | null
  open: boolean
  onOpenChange: (open: boolean) => void
  onInstalled: () => void
}

function InstallModal({ connector, open, onOpenChange, onInstalled }: InstallModalProps) {
  const [baseUrl, setBaseUrl] = useState('')
  const [secretValue, setSecretValue] = useState('')
  const [headerName, setHeaderName] = useState('')
  const [environment, setEnvironment] = useState<'development' | 'staging' | 'production'>(
    'development'
  )
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const needsSecret = connector?.primaryAuth !== 'none'
  const needsHeaderName = connector?.primaryAuth === 'api-key'

  const handleInstall = async () => {
    if (!connector) return
    if (!baseUrl.trim()) {
      setError('Base URL is required.')
      return
    }
    if (needsSecret && !secretValue.trim()) {
      setError('Credential value is required.')
      return
    }

    setLoading(true)
    setError(null)

    try {
      const payload = buildInstallPayload(connector, {
        baseUrl: baseUrl.trim(),
        secretValue: secretValue.trim(),
        headerName: needsHeaderName ? headerName.trim() || 'X-API-Key' : undefined,
        environment,
      })
      await createConnector({ ...payload, active: true })
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
    setSecretValue('')
    setHeaderName('')
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
            Register this connector for your tenant. Supply the runtime endpoint and
            credential — these are stored encrypted and never returned by the API.
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

          {needsHeaderName && (
            <div className="space-y-1.5">
              <Label htmlFor="marketplace-headerName">
                API Key Header{' '}
                <span className="text-muted-foreground font-normal">(default: X-API-Key)</span>
              </Label>
              <Input
                id="marketplace-headerName"
                placeholder="X-API-Key"
                value={headerName}
                onChange={(e) => setHeaderName(e.target.value)}
              />
            </div>
          )}

          {needsSecret && (
            <div className="space-y-1.5">
              <Label htmlFor="marketplace-secret">
                {authLabel(connector?.primaryAuth ?? 'bearer')} credential
              </Label>
              <Input
                id="marketplace-secret"
                type="password"
                placeholder="Paste your credential value"
                value={secretValue}
                onChange={(e) => setSecretValue(e.target.value)}
                autoComplete="new-password"
              />
              <p className="text-xs text-muted-foreground flex items-center gap-1">
                <ShieldCheck className="h-3 w-3 shrink-0" />
                Stored encrypted at rest. Never returned by the API.
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
            <Button onClick={handleInstall} disabled={loading}>
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
  onInstall: (connector: MarketplaceConnector) => void
}

function ConnectorCard({ connector, onInstall }: ConnectorCardProps) {
  return (
    <Card className="flex flex-col">
      <CardHeader className="pb-2">
        <div className="flex items-start justify-between gap-2">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <CardTitle className="text-base leading-tight">{connector.displayName}</CardTitle>
              {connector.source === 'official' && (
                <Badge
                  variant="default"
                  className="text-xs shrink-0"
                  style={{ background: 'hsl(var(--primary))' }}
                >
                  Official
                </Badge>
              )}
            </div>
            <CardDescription className="mt-1 text-xs">
              v{connector.version} &middot; {connector.vendor}
            </CardDescription>
          </div>
        </div>
      </CardHeader>

      <CardContent className="flex-1 flex flex-col gap-3 text-sm">
        <p className="text-muted-foreground text-xs leading-relaxed line-clamp-3">
          {connector.description}
        </p>

        <div className="flex flex-wrap gap-1.5">
          <Badge variant={transportBadgeVariant(connector.transport)} className="text-xs">
            {transportLabel(connector.transport)}
          </Badge>
          <Badge variant="outline" className="text-xs">
            {authLabel(connector.primaryAuth)}
          </Badge>
          <Badge variant="secondary" className="text-xs">
            {connector.operationCount} op{connector.operationCount !== 1 ? 's' : ''}
          </Badge>
        </div>

        <div className="flex flex-wrap gap-1">
          {connector.tags.slice(0, 4).map((tag) => (
            <span
              key={tag}
              className="text-xs text-muted-foreground bg-muted px-1.5 py-0.5 rounded"
            >
              {tag}
            </span>
          ))}
        </div>

        <div className="mt-auto flex items-center justify-between pt-2">
          {connector.documentationUrl ? (
            <a
              href={connector.documentationUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="text-xs text-muted-foreground hover:text-foreground flex items-center gap-1 transition-colors"
            >
              <ExternalLink className="h-3 w-3" />
              Docs
            </a>
          ) : (
            <span />
          )}
          <Button size="sm" className="h-7 text-xs gap-1.5" onClick={() => onInstall(connector)}>
            <Download className="h-3.5 w-3.5" />
            Install
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}

// ─── Page ─────────────────────────────────────────────────────────────────────

export default function MarketplacePage() {
  const { status } = useSession()
  const [installing, setInstalling] = useState<MarketplaceConnector | null>(null)
  const [installedKeys, setInstalledKeys] = useState<Set<string>>(new Set())

  if (status === 'loading') {
    return (
      <div className="flex items-center justify-center py-24">
        <RefreshCw className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    )
  }

  const official = MARKETPLACE_CATALOG.filter((c) => c.source === 'official')
  const community = MARKETPLACE_CATALOG.filter((c) => c.source === 'community')

  const handleInstalled = (key: string) => {
    setInstalledKeys((prev) => new Set(Array.from(prev).concat(key)))
  }

  return (
    <PageSurface>
    <div className="space-y-8">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-foreground">Marketplace</h1>
        <p className="text-muted-foreground mt-1">
          Browse and install connector definitions maintained by the Werkflow core team and community.
        </p>
      </div>

      {/* Official connectors */}
      <section className="space-y-4">
        <div>
          <h2 className="text-lg font-semibold text-foreground">Official Connectors</h2>
          <p className="text-sm text-muted-foreground">
            Maintained by the Werkflow core team. Guaranteed to be compatible with the current
            platform version.
          </p>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {official.map((connector) => (
            <div key={connector.key} className="relative">
              <ConnectorCard connector={connector} onInstall={setInstalling} />
              {installedKeys.has(connector.key) && (
                <div className="absolute inset-0 rounded-xl bg-background/60 flex items-center justify-center">
                  <Badge
                    variant="default"
                    className="text-sm px-4 py-1.5"
                    style={{ background: 'hsl(var(--primary))' }}
                  >
                    Installed
                  </Badge>
                </div>
              )}
            </div>
          ))}
        </div>
      </section>

      {/* Community connectors */}
      <section className="space-y-4">
        <div>
          <h2 className="text-lg font-semibold text-foreground">Community Connectors</h2>
          <p className="text-sm text-muted-foreground">
            Contributed by the open-source community. Review the connector definition before
            installing in a production environment.
          </p>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {community.map((connector) => (
            <div key={connector.key} className="relative">
              <ConnectorCard connector={connector} onInstall={setInstalling} />
              {installedKeys.has(connector.key) && (
                <div className="absolute inset-0 rounded-xl bg-background/60 flex items-center justify-center">
                  <Badge
                    variant="default"
                    className="text-sm px-4 py-1.5"
                    style={{ background: 'hsl(var(--primary))' }}
                  >
                    Installed
                  </Badge>
                </div>
              )}
            </div>
          ))}
        </div>
      </section>

      {/* Contribute CTA */}
      <Card className="border-dashed">
        <CardContent className="pt-6 pb-6 text-center space-y-3">
          <p className="font-semibold text-foreground">Want to contribute a connector?</p>
          <p className="text-sm text-muted-foreground max-w-md mx-auto">
            The marketplace is open-source. Follow the contribution guide to submit your connector
            definition via pull request.
          </p>
          <a
            href="https://github.com/werkflow-platform/werkflow-public/blob/main/marketplace/CONTRIBUTING.md"
            target="_blank"
            rel="noopener noreferrer"
          >
            <Button variant="outline" size="sm" className="gap-2">
              <ExternalLink className="h-4 w-4" />
              Contribution Guide
            </Button>
          </a>
        </CardContent>
      </Card>

      {/* Install modal */}
      <InstallModal
        connector={installing}
        open={installing !== null}
        onOpenChange={(open) => {
          if (!open) setInstalling(null)
        }}
        onInstalled={() => {
          if (installing) handleInstalled(installing.key)
        }}
      />
    </div>
    </PageSurface>
  )
}
