'use client'

import { useState, useEffect } from 'react'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
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
import { Badge } from '@/components/ui/badge'
import { useUpdateServiceUrl, useTestServiceConnectivity } from '@/lib/hooks/useServiceRegistry'
import { Loader2, CheckCircle2, XCircle } from 'lucide-react'
import type { Service } from '@/lib/api/services'

interface ServiceEditModalProps {
  service: Service
  open: boolean
  onOpenChange: (open: boolean) => void
}

export default function ServiceEditModal({
  service,
  open,
  onOpenChange
}: ServiceEditModalProps) {
  const [baseUrl, setBaseUrl] = useState(service.baseUrl)
  const [environment, setEnvironment] = useState(service.environment)
  const [testResult, setTestResult] = useState<{
    online: boolean
    responseTime: number
    error?: string
  } | null>(null)

  const updateUrlMutation = useUpdateServiceUrl()
  const testConnectivityMutation = useTestServiceConnectivity()

  useEffect(() => {
    setBaseUrl(service.baseUrl)
    setEnvironment(service.environment)
    setTestResult(null)
  }, [service, open])

  const handleTestConnection = async () => {
    if (!baseUrl) return
    setTestResult(null)
    const result = await testConnectivityMutation.mutateAsync(baseUrl)
    setTestResult(result)
  }

  const handleSave = async () => {
    if (!baseUrl) return
    try {
      await updateUrlMutation.mutateAsync({
        serviceId: service.id,
        baseUrl,
        environment: environment || 'development'
      })
      onOpenChange(false)
    } catch (error) {
      console.error('Failed to update service URL:', error)
      alert('Failed to update service URL. Please try again.')
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[500px]">
        <DialogHeader>
          <DialogTitle>Edit Service Configuration</DialogTitle>
          <DialogDescription>
            Update the base URL and environment for {service.displayName}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-4">
          {/* Service Name (Read-only) */}
          <div className="space-y-2">
            <Label>Service Name</Label>
            <Input value={service.displayName} disabled />
          </div>

          {/* Current URL (Read-only) */}
          <div className="space-y-2">
            <Label>Current URL</Label>
            <code className="block text-xs bg-muted px-3 py-2 rounded">
              {service.baseUrl}
            </code>
          </div>

          {/* New Base URL */}
          <div className="space-y-2">
            <Label htmlFor="baseUrl">New Base URL</Label>
            <Input
              id="baseUrl"
              value={baseUrl}
              onChange={(e) => setBaseUrl(e.target.value)}
              placeholder="http://service-name:8080/api"
            />
            <p className="text-xs text-muted-foreground">
              Enter the complete base URL including protocol and path
            </p>
          </div>

          {/* Environment */}
          <div className="space-y-2">
            <Label htmlFor="environment">Environment</Label>
            <Select value={environment} onValueChange={(value: any) => setEnvironment(value)}>
              <SelectTrigger id="environment">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="development">Development</SelectItem>
                <SelectItem value="staging">Staging</SelectItem>
                <SelectItem value="production">Production</SelectItem>
              </SelectContent>
            </Select>
          </div>

          {/* Test Connection */}
          <div className="space-y-2">
            <Button
              variant="outline"
              onClick={handleTestConnection}
              disabled={testConnectivityMutation.isPending || !baseUrl}
              className="w-full"
            >
              {testConnectivityMutation.isPending && (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              )}
              Test Connection
            </Button>

            {testResult && (
              <div className="flex items-center gap-2 p-3 rounded border">
                {testResult.online ? (
                  <>
                    <CheckCircle2 className="h-4 w-4 text-green-500" />
                    <div className="flex-1">
                      <p className="text-sm font-medium text-green-700">
                        Connection Successful
                      </p>
                      <p className="text-xs text-muted-foreground">
                        Response time: {testResult.responseTime}ms
                      </p>
                    </div>
                  </>
                ) : (
                  <>
                    <XCircle className="h-4 w-4 text-red-500" />
                    <div className="flex-1">
                      <p className="text-sm font-medium text-red-700">
                        Connection Failed
                      </p>
                      <p className="text-xs text-muted-foreground">
                        {testResult.error || 'Service is unreachable'}
                      </p>
                    </div>
                  </>
                )}
              </div>
            )}
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button
            onClick={handleSave}
            disabled={updateUrlMutation.isPending || baseUrl === service.baseUrl}
          >
            {updateUrlMutation.isPending && (
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            )}
            Save Changes
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
