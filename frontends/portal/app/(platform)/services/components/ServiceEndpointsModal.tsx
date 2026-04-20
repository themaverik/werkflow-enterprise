'use client'

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { useServiceEndpoints } from '@/lib/hooks/useServiceRegistry'
import { Copy, CheckCircle2, Loader2 } from 'lucide-react'
import { useState } from 'react'
import type { Service, ServiceEndpoint } from '@/lib/api/services'

interface ServiceEndpointsModalProps {
  service: Service
  open: boolean
  onOpenChange: (open: boolean) => void
}

export default function ServiceEndpointsModal({
  service,
  open,
  onOpenChange
}: ServiceEndpointsModalProps) {
  const { data: endpoints, isLoading } = useServiceEndpoints(service.id)
  const [copiedEndpoint, setCopiedEndpoint] = useState<string | null>(null)

  const copyToClipboard = async (text: string, endpointPath: string) => {
    try {
      await navigator.clipboard.writeText(text)
      setCopiedEndpoint(endpointPath)
      setTimeout(() => setCopiedEndpoint(null), 2000)
    } catch (error) {
      console.error('Failed to copy:', error)
    }
  }

  const getMethodColor = (method: string) => {
    const colors: Record<string, string> = {
      GET: 'bg-blue-500',
      POST: 'bg-green-500',
      PUT: 'bg-yellow-500',
      DELETE: 'bg-red-500',
      PATCH: 'bg-purple-500'
    }
    return colors[method] || 'bg-gray-500'
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[700px] max-h-[80vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{service.displayName} - API Endpoints</DialogTitle>
          <DialogDescription>
            Available endpoints and their documentation
          </DialogDescription>
        </DialogHeader>

        {isLoading && (
          <div className="flex items-center justify-center py-8">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
          </div>
        )}

        {!isLoading && endpoints && endpoints.length === 0 && (
          <div className="text-center py-8 text-muted-foreground">
            No endpoints available
          </div>
        )}

        {!isLoading && endpoints && endpoints.length > 0 && (
          <div className="space-y-4">
            {endpoints.map((endpoint, index) => (
              <Card key={index}>
                <CardHeader>
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <Badge className={getMethodColor(endpoint.httpMethod)}>
                        {endpoint.httpMethod}
                      </Badge>
                      <code className="text-sm font-mono">
                        {endpoint.endpointPath}
                      </code>
                    </div>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() =>
                        copyToClipboard(
                          `${service.baseUrl || ''}${endpoint.endpointPath}`,
                          endpoint.endpointPath
                        )
                      }
                    >
                      {copiedEndpoint === endpoint.endpointPath ? (
                        <CheckCircle2 className="h-4 w-4 text-green-500" />
                      ) : (
                        <Copy className="h-4 w-4" />
                      )}
                    </Button>
                  </div>
                  <CardDescription>{endpoint.description}</CardDescription>
                </CardHeader>

                <CardContent className="space-y-3">
                  {/* Full URL */}
                  <div>
                    <p className="text-xs font-medium text-muted-foreground mb-1">
                      Full URL
                    </p>
                    <code className="text-xs bg-muted px-2 py-1 rounded block">
                      {service.baseUrl || ''}{endpoint.endpointPath}
                    </code>
                  </div>

                  {/* Parameters */}
                  {endpoint.parameters && endpoint.parameters.length > 0 && (
                    <div>
                      <p className="text-xs font-medium text-muted-foreground mb-2">
                        Parameters
                      </p>
                      <div className="space-y-2">
                        {endpoint.parameters.map((param, paramIndex) => (
                          <div
                            key={paramIndex}
                            className="flex items-start gap-2 text-xs"
                          >
                            <code className="font-mono bg-muted px-2 py-0.5 rounded">
                              {param.name}
                            </code>
                            <Badge variant="outline" className="text-xs">
                              {param.type}
                            </Badge>
                            {param.required && (
                              <Badge variant="destructive" className="text-xs">
                                required
                              </Badge>
                            )}
                            <span className="text-muted-foreground flex-1">
                              {param.description}
                            </span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Example Request */}
                  {endpoint.exampleRequest && (
                    <div>
                      <p className="text-xs font-medium text-muted-foreground mb-1">
                        Example Request
                      </p>
                      <pre className="text-xs bg-muted p-2 rounded overflow-x-auto">
                        {endpoint.exampleRequest}
                      </pre>
                    </div>
                  )}

                  {/* Example Response */}
                  {endpoint.exampleResponse && (
                    <div>
                      <p className="text-xs font-medium text-muted-foreground mb-1">
                        Example Response
                      </p>
                      <pre className="text-xs bg-muted p-2 rounded overflow-x-auto">
                        {endpoint.exampleResponse}
                      </pre>
                    </div>
                  )}
                </CardContent>
              </Card>
            ))}
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
