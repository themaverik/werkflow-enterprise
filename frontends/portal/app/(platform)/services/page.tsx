'use client'

import { useState } from 'react'
import { useServices } from '@/lib/hooks/useServiceRegistry'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Plus, Search, RefreshCw, ExternalLink, Clock, Activity } from 'lucide-react'
import ServiceCard from './components/ServiceCard'
import ServiceEditModal from './components/ServiceEditModal'
import ServiceEndpointsModal from './components/ServiceEndpointsModal'
import type { Service } from '@/lib/api/services'

/**
 * Service Registry Page
 *
 * Centralized management of all microservices in the Werkflow platform.
 * Enables no-code service URL configuration and endpoint discovery.
 *
 * Features:
 * - View all registered services
 * - Edit service URLs per environment
 * - Test service connectivity
 * - View service endpoints and documentation
 * - Monitor service health
 */
export default function ServicesPage() {
  const { data: services, isLoading, error, refetch } = useServices()
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedService, setSelectedService] = useState<Service | null>(null)
  const [isEditModalOpen, setIsEditModalOpen] = useState(false)
  const [isEndpointsModalOpen, setIsEndpointsModalOpen] = useState(false)

  const handleEditService = (service: Service) => {
    setSelectedService(service)
    setIsEditModalOpen(true)
  }

  const handleViewEndpoints = (service: Service) => {
    setSelectedService(service)
    setIsEndpointsModalOpen(true)
  }

  const filteredServices = services?.filter(service =>
    service.displayName.toLowerCase().includes(searchQuery.toLowerCase()) ||
    service.serviceName.toLowerCase().includes(searchQuery.toLowerCase()) ||
    service.description.toLowerCase().includes(searchQuery.toLowerCase())
  )

  const activeServicesCount = services?.filter(s => s.healthStatus === 'HEALTHY').length || 0
  const totalServicesCount = services?.length || 0
  const avgResponseTime = services?.length
    ? Math.round(services.reduce((sum, s) => sum + (s.responseTime || 0), 0) / services.length)
    : 0

  return (
    <div className="container mx-auto p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Service Registry</h1>
          <p className="text-muted-foreground mt-1">
            Manage microservices and configure service endpoints
          </p>
        </div>
        <Button onClick={() => refetch()} variant="outline">
          <RefreshCw className="h-4 w-4 mr-2" />
          Refresh
        </Button>
      </div>

      {/* Statistics */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card>
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-muted-foreground">Total Services</p>
                <p className="text-2xl font-bold">{totalServicesCount}</p>
              </div>
              <Activity className="h-8 w-8 text-muted-foreground" />
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-muted-foreground">Active Services</p>
                <p className="text-2xl font-bold">{activeServicesCount}</p>
              </div>
              <div className="h-3 w-3 rounded-full bg-green-500 animate-pulse" />
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-muted-foreground">Avg Response Time</p>
                <p className="text-2xl font-bold">{avgResponseTime}ms</p>
              </div>
              <Clock className="h-8 w-8 text-muted-foreground" />
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Search and Filters */}
      <Card>
        <CardContent className="pt-6">
          <div className="flex items-center gap-4">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder="Search services by name or description..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="pl-10"
              />
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Services Grid */}
      {isLoading && (
        <div className="text-center py-12">
          <RefreshCw className="h-8 w-8 animate-spin mx-auto text-muted-foreground" />
          <p className="text-muted-foreground mt-2">Loading services...</p>
        </div>
      )}

      {error && (
        <Card className="border-destructive">
          <CardContent className="pt-6">
            <div className="space-y-3">
              <p className="text-destructive font-semibold">Error loading services</p>
              <p className="text-sm text-muted-foreground">{error.message}</p>
              {error.message.includes('backend service') && (
                <div className="mt-4 p-3 bg-yellow-50 border border-yellow-200 rounded-md">
                  <p className="text-sm text-yellow-800">
                    <strong>Backend Service Not Available</strong>
                    <br />
                    Please ensure the Werkflow Engine service is running at http://localhost:8081
                  </p>
                  <Button
                    variant="outline"
                    size="sm"
                    className="mt-2"
                    onClick={() => refetch()}
                  >
                    Retry Connection
                  </Button>
                </div>
              )}
            </div>
          </CardContent>
        </Card>
      )}

      {!isLoading && !error && filteredServices && filteredServices.length === 0 && (
        <Card>
          <CardContent className="pt-6 text-center py-12">
            <p className="text-muted-foreground">No services found</p>
            {searchQuery && (
              <Button
                variant="link"
                onClick={() => setSearchQuery('')}
                className="mt-2"
              >
                Clear search
              </Button>
            )}
          </CardContent>
        </Card>
      )}

      {!isLoading && !error && filteredServices && filteredServices.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredServices.map((service) => (
            <ServiceCard
              key={service.id}
              service={service}
              onEdit={handleEditService}
              onViewEndpoints={handleViewEndpoints}
            />
          ))}
        </div>
      )}

      {/* Modals */}
      {selectedService && (
        <>
          <ServiceEditModal
            service={selectedService}
            open={isEditModalOpen}
            onOpenChange={setIsEditModalOpen}
          />
          <ServiceEndpointsModal
            service={selectedService}
            open={isEndpointsModalOpen}
            onOpenChange={setIsEndpointsModalOpen}
          />
        </>
      )}
    </div>
  )
}
