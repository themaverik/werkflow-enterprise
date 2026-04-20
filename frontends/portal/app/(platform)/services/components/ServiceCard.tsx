'use client'

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Edit, ExternalLink, Clock, Activity, CheckCircle2, XCircle, AlertCircle } from 'lucide-react'
import type { Service } from '@/lib/api/services'

interface ServiceCardProps {
  service: Service
  onEdit: (service: Service) => void
  onViewEndpoints: (service: Service) => void
}

export default function ServiceCard({ service, onEdit, onViewEndpoints }: ServiceCardProps) {
  const getStatusIcon = () => {
    switch (service.status) {
      case 'active':
        return <CheckCircle2 className="h-4 w-4 text-green-500" />
      case 'inactive':
        return <XCircle className="h-4 w-4 text-red-500" />
      case 'maintenance':
        return <AlertCircle className="h-4 w-4 text-yellow-500" />
      default:
        return null
    }
  }

  const getStatusBadge = () => {
    switch (service.status) {
      case 'active':
        return <Badge variant="success">Active</Badge>
      case 'inactive':
        return <Badge variant="destructive">Inactive</Badge>
      case 'maintenance':
        return <Badge variant="warning">Maintenance</Badge>
      default:
        return <Badge variant="secondary">{service.status}</Badge>
    }
  }

  const getEnvironmentBadge = () => {
    if (!service.environment) return null

    const colors: Record<string, string> = {
      development: 'bg-blue-500',
      staging: 'bg-yellow-500',
      production: 'bg-green-500'
    }

    const colorClass = colors[service.environment] || 'bg-gray-500'

    return (
      <Badge variant="outline" className={colorClass}>
        {service.environment}
      </Badge>
    )
  }

  return (
    <Card className="hover:shadow-lg transition-shadow">
      <CardHeader>
        <div className="flex items-start justify-between">
          <div className="flex-1">
            <CardTitle className="flex items-center gap-2">
              {service.displayName}
              {getStatusIcon()}
            </CardTitle>
            <CardDescription className="mt-1">
              {service.description}
            </CardDescription>
          </div>
        </div>
        <div className="flex items-center gap-2 mt-2">
          {getStatusBadge()}
          {getEnvironmentBadge()}
          {service.version && (
            <Badge variant="outline">v{service.version}</Badge>
          )}
        </div>
      </CardHeader>

      <CardContent className="space-y-4">
        {/* Service URL */}
        <div>
          <p className="text-xs text-muted-foreground font-medium mb-1">Base URL</p>
          <div className="flex items-center gap-2">
            <code className="text-xs bg-muted px-2 py-1 rounded flex-1 truncate">
              {service.baseUrl}
            </code>
            <Button
              variant="ghost"
              size="sm"
              className="h-6 w-6 p-0"
              onClick={() => window.open(service.baseUrl, '_blank')}
            >
              <ExternalLink className="h-3 w-3" />
            </Button>
          </div>
        </div>

        {/* Metrics */}
        {service.responseTime !== undefined && (
          <div className="flex items-center gap-4 text-xs text-muted-foreground">
            <div className="flex items-center gap-1">
              <Clock className="h-3 w-3" />
              <span>{service.responseTime}ms</span>
            </div>
            <div className="flex items-center gap-1">
              <Activity className="h-3 w-3" />
              <span>{service.endpoints?.length || 0} endpoints</span>
            </div>
          </div>
        )}

        {/* Tags */}
        {service.tags && service.tags.length > 0 && (
          <div className="flex flex-wrap gap-1">
            {service.tags.map((tag) => (
              <Badge key={tag} variant="outline" className="text-xs">
                {tag}
              </Badge>
            ))}
          </div>
        )}

        {/* Actions */}
        <div className="flex gap-2 pt-2">
          <Button
            variant="outline"
            size="sm"
            className="flex-1"
            onClick={() => onEdit(service)}
          >
            <Edit className="h-3 w-3 mr-2" />
            Edit
          </Button>
          <Button
            variant="default"
            size="sm"
            className="flex-1"
            onClick={() => onViewEndpoints(service)}
          >
            <ExternalLink className="h-3 w-3 mr-2" />
            Endpoints
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}
