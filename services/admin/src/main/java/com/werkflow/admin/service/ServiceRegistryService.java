package com.werkflow.admin.service;

import com.werkflow.admin.entity.serviceregistry.*;
import com.werkflow.admin.exception.DuplicateServiceException;
import com.werkflow.admin.exception.EnvironmentNotConfiguredException;
import com.werkflow.admin.exception.ServiceNotFoundException;
import com.werkflow.admin.exception.ServiceRegistryException;
import com.werkflow.admin.repository.serviceregistry.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service class for managing service registry operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ServiceRegistryService {

    private final ServiceRegistryRepository serviceRegistryRepository;
    private final ServiceEndpointRepository serviceEndpointRepository;
    private final ServiceEnvironmentUrlRepository serviceEnvironmentUrlRepository;
    private final ServiceHealthCheckRepository serviceHealthCheckRepository;
    private final RestTemplate restTemplate;

    /**
     * Register a new service in the registry
     * @param service The service to register
     * @return The registered service
     * @throws DuplicateServiceException if a service with the same name already exists
     */
    @Transactional
    public ServiceRegistry registerService(ServiceRegistry service) {
        log.info("Registering new service: {}", service.getServiceName());

        if (serviceRegistryRepository.existsByServiceName(service.getServiceName())) {
            log.error("Service already exists: {}", service.getServiceName());
            throw new DuplicateServiceException(service.getServiceName());
        }

        ServiceRegistry savedService = serviceRegistryRepository.save(service);
        log.info("Successfully registered service: {} with ID: {}",
                 savedService.getServiceName(), savedService.getId());

        return savedService;
    }

    /**
     * Resolve the full service URL for a given service name and environment
     * @param serviceName The service name
     * @param environment The environment
     * @return The full service URL (base_url + base_path)
     * @throws ServiceNotFoundException if the service is not found
     * @throws EnvironmentNotConfiguredException if the environment is not configured
     */
    public String resolveServiceUrl(String serviceName, Environment environment) {
        log.debug("Resolving URL for service: {} in environment: {}", serviceName, environment);

        ServiceRegistry service = serviceRegistryRepository.findByServiceName(serviceName)
            .orElseThrow(() -> new ServiceNotFoundException(serviceName));

        ServiceEnvironmentUrl envUrl = serviceEnvironmentUrlRepository
            .findFirstByServiceIdAndEnvironmentAndActiveTrueOrderByPriorityAsc(service.getId(), environment)
            .orElseThrow(() -> new EnvironmentNotConfiguredException(serviceName, environment.name()));

        String fullUrl = envUrl.getBaseUrl() + service.getBasePath();
        log.debug("Resolved URL for {}: {}", serviceName, fullUrl);

        return fullUrl;
    }

    /**
     * Perform a health check on a service in a specific environment
     * @param serviceId The service ID
     * @param environment The environment to check
     * @return The health check result
     */
    @Transactional
    public ServiceHealthCheck performHealthCheck(UUID serviceId, Environment environment) {
        log.info("Performing health check for service ID: {} in environment: {}", serviceId, environment);

        ServiceRegistry service = serviceRegistryRepository.findById(serviceId)
            .orElseThrow(() -> new ServiceNotFoundException(serviceId));

        ServiceEnvironmentUrl envUrl = serviceEnvironmentUrlRepository
            .findFirstByServiceIdAndEnvironmentAndActiveTrueOrderByPriorityAsc(serviceId, environment)
            .orElseThrow(() -> new EnvironmentNotConfiguredException(
                service.getServiceName(), environment.name()));

        ServiceHealthCheck healthCheck = ServiceHealthCheck.builder()
            .service(service)
            .environment(environment)
            .checkedAt(OffsetDateTime.now())
            .build();

        try {
            String healthCheckUrl = envUrl.getBaseUrl() +
                (service.getHealthCheckUrl() != null ? service.getHealthCheckUrl() : "/actuator/health");

            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.getForEntity(healthCheckUrl, String.class);
            long responseTime = System.currentTimeMillis() - startTime;

            if (response.getStatusCode() == HttpStatus.OK) {
                healthCheck.setStatus(HealthStatus.HEALTHY);
                healthCheck.setResponseTimeMs((int) responseTime);
                log.info("Service {} is HEALTHY ({}ms)", service.getServiceName(), responseTime);
            } else {
                healthCheck.setStatus(HealthStatus.DEGRADED);
                healthCheck.setResponseTimeMs((int) responseTime);
                healthCheck.setErrorMessage("Unexpected status code: " + response.getStatusCode());
                log.warn("Service {} is DEGRADED: {}", service.getServiceName(), response.getStatusCode());
            }

        } catch (Exception e) {
            healthCheck.setStatus(HealthStatus.UNHEALTHY);
            healthCheck.setErrorMessage(e.getMessage());
            log.error("Service {} is UNHEALTHY: {}", service.getServiceName(), e.getMessage());
        }

        // Save health check record
        ServiceHealthCheck savedHealthCheck = serviceHealthCheckRepository.save(healthCheck);

        // Update service health status
        updateServiceHealthStatus(serviceId, healthCheck.getStatus());

        return savedHealthCheck;
    }

    /**
     * Get all endpoints for a specific service
     * @param serviceId The service ID
     * @return List of endpoints
     */
    public List<ServiceEndpoint> getServiceEndpoints(UUID serviceId) {
        log.debug("Fetching endpoints for service ID: {}", serviceId);

        if (!serviceRegistryRepository.existsById(serviceId)) {
            throw new ServiceNotFoundException(serviceId);
        }

        return serviceEndpointRepository.findByServiceIdAndActiveTrue(serviceId);
    }

    /**
     * Get all active services
     * @return List of active services
     */
    public List<ServiceRegistry> getAllActiveServices() {
        log.debug("Fetching all active services");
        return serviceRegistryRepository.findByActiveTrue();
    }

    /**
     * Get service by ID
     * @param serviceId The service ID
     * @return The service
     */
    public ServiceRegistry getServiceById(UUID serviceId) {
        log.debug("Fetching service by ID: {}", serviceId);
        return serviceRegistryRepository.findById(serviceId)
            .orElseThrow(() -> new ServiceNotFoundException(serviceId));
    }

    /**
     * Get service by name
     * @param serviceName The service name
     * @return The service
     */
    public ServiceRegistry getServiceByName(String serviceName) {
        log.debug("Fetching service by name: {}", serviceName);
        return serviceRegistryRepository.findByServiceName(serviceName)
            .orElseThrow(() -> new ServiceNotFoundException(serviceName));
    }

    /**
     * Get all services with pagination
     * @param pageable Pagination information
     * @return Page of services
     */
    public Page<ServiceRegistry> getAllServices(Pageable pageable) {
        log.debug("Fetching all services with pagination");
        return serviceRegistryRepository.findAll(pageable);
    }

    /**
     * Update service health status
     * @param serviceId The service ID
     * @param status The new health status
     */
    @Transactional
    public void updateServiceHealthStatus(UUID serviceId, HealthStatus status) {
        log.debug("Updating health status for service ID: {} to {}", serviceId, status);

        ServiceRegistry service = serviceRegistryRepository.findById(serviceId)
            .orElseThrow(() -> new ServiceNotFoundException(serviceId));

        service.setHealthStatus(status);
        service.setLastHealthCheckAt(OffsetDateTime.now());
        serviceRegistryRepository.save(service);

        log.info("Updated health status for service {} to {}", service.getServiceName(), status);
    }

    /**
     * Update an existing service
     * @param serviceId The service ID
     * @param updatedService The updated service data
     * @return The updated service
     */
    @Transactional
    public ServiceRegistry updateService(UUID serviceId, ServiceRegistry updatedService) {
        log.info("Updating service ID: {}", serviceId);

        ServiceRegistry existingService = serviceRegistryRepository.findById(serviceId)
            .orElseThrow(() -> new ServiceNotFoundException(serviceId));

        // Check for duplicate service name if name is being changed
        if (!existingService.getServiceName().equals(updatedService.getServiceName()) &&
            serviceRegistryRepository.existsByServiceName(updatedService.getServiceName())) {
            throw new DuplicateServiceException(updatedService.getServiceName());
        }

        // Update fields
        existingService.setServiceName(updatedService.getServiceName());
        existingService.setDisplayName(updatedService.getDisplayName());
        existingService.setDescription(updatedService.getDescription());
        existingService.setServiceType(updatedService.getServiceType());
        existingService.setBasePath(updatedService.getBasePath());
        existingService.setVersion(updatedService.getVersion());
        existingService.setHealthCheckUrl(updatedService.getHealthCheckUrl());
        existingService.setActive(updatedService.getActive());

        if (updatedService.getOwner() != null) {
            existingService.setOwner(updatedService.getOwner());
        }

        ServiceRegistry savedService = serviceRegistryRepository.save(existingService);
        log.info("Successfully updated service: {}", savedService.getServiceName());

        return savedService;
    }

    /**
     * Delete a service
     * @param serviceId The service ID
     */
    @Transactional
    public void deleteService(UUID serviceId) {
        log.info("Deleting service ID: {}", serviceId);

        ServiceRegistry service = serviceRegistryRepository.findById(serviceId)
            .orElseThrow(() -> new ServiceNotFoundException(serviceId));

        serviceRegistryRepository.delete(service);
        log.info("Successfully deleted service: {}", service.getServiceName());
    }

    /**
     * Search services by name or display name
     * @param searchTerm The search term
     * @param pageable Pagination information
     * @return Page of matching services
     */
    public Page<ServiceRegistry> searchServices(String searchTerm, Pageable pageable) {
        log.debug("Searching services with term: {}", searchTerm);
        return serviceRegistryRepository.searchByNameOrDisplayName(searchTerm, pageable);
    }

    /**
     * Get health check history for a service
     * @param serviceId The service ID
     * @param pageable Pagination information
     * @return Page of health checks
     */
    public Page<ServiceHealthCheck> getHealthCheckHistory(UUID serviceId, Pageable pageable) {
        log.debug("Fetching health check history for service ID: {}", serviceId);

        if (!serviceRegistryRepository.existsById(serviceId)) {
            throw new ServiceNotFoundException(serviceId);
        }

        return serviceHealthCheckRepository.findRecentByServiceId(serviceId, pageable);
    }

    /**
     * Get services by type
     * @param serviceType The service type
     * @return List of services of the specified type
     */
    public List<ServiceRegistry> getServicesByType(ServiceType serviceType) {
        log.debug("Fetching services by type: {}", serviceType);
        return serviceRegistryRepository.findByServiceTypeAndActive(serviceType, true);
    }

    /**
     * Create a new endpoint for a service
     * @param serviceId The service ID
     * @param endpoint The endpoint to create
     * @return The created endpoint
     */
    @Transactional
    public ServiceEndpoint createEndpoint(UUID serviceId, ServiceEndpoint endpoint) {
        log.info("Creating endpoint for service ID: {}", serviceId);

        ServiceRegistry service = serviceRegistryRepository.findById(serviceId)
                .orElseThrow(() -> new ServiceNotFoundException(serviceId));

        // Check for duplicate endpoint
        if (serviceEndpointRepository.existsByServiceIdAndEndpointPathAndHttpMethod(
                serviceId, endpoint.getEndpointPath(), endpoint.getHttpMethod())) {
            throw new DuplicateServiceException(
                    String.format("Endpoint %s %s already exists for service %s",
                            endpoint.getHttpMethod(), endpoint.getEndpointPath(), service.getServiceName()));
        }

        endpoint.setService(service);
        ServiceEndpoint savedEndpoint = serviceEndpointRepository.save(endpoint);

        log.info("Successfully created endpoint: {} {}", savedEndpoint.getHttpMethod(), savedEndpoint.getEndpointPath());
        return savedEndpoint;
    }

    /**
     * Get endpoint by ID
     * @param endpointId The endpoint ID
     * @return The endpoint
     */
    public ServiceEndpoint getEndpointById(UUID endpointId) {
        log.debug("Fetching endpoint by ID: {}", endpointId);
        return serviceEndpointRepository.findById(endpointId)
                .orElseThrow(() -> new ServiceNotFoundException("Endpoint not found with ID: " + endpointId));
    }

    /**
     * Update an existing endpoint
     * @param endpointId The endpoint ID
     * @param updatedEndpoint The updated endpoint data
     * @return The updated endpoint
     */
    @Transactional
    public ServiceEndpoint updateEndpoint(UUID endpointId, ServiceEndpoint updatedEndpoint) {
        log.info("Updating endpoint ID: {}", endpointId);

        ServiceEndpoint existingEndpoint = serviceEndpointRepository.findById(endpointId)
                .orElseThrow(() -> new ServiceNotFoundException("Endpoint not found with ID: " + endpointId));

        // Check for duplicate endpoint if path or method changed
        if (!existingEndpoint.getEndpointPath().equals(updatedEndpoint.getEndpointPath()) ||
                !existingEndpoint.getHttpMethod().equals(updatedEndpoint.getHttpMethod())) {
            if (serviceEndpointRepository.existsByServiceIdAndEndpointPathAndHttpMethod(
                    existingEndpoint.getService().getId(),
                    updatedEndpoint.getEndpointPath(),
                    updatedEndpoint.getHttpMethod())) {
                throw new DuplicateServiceException(
                        String.format("Endpoint %s %s already exists",
                                updatedEndpoint.getHttpMethod(), updatedEndpoint.getEndpointPath()));
            }
        }

        // Update fields
        existingEndpoint.setEndpointPath(updatedEndpoint.getEndpointPath());
        existingEndpoint.setHttpMethod(updatedEndpoint.getHttpMethod());
        existingEndpoint.setDescription(updatedEndpoint.getDescription());
        existingEndpoint.setRequiresAuth(updatedEndpoint.getRequiresAuth());
        existingEndpoint.setTimeoutSeconds(updatedEndpoint.getTimeoutSeconds());
        existingEndpoint.setRetryCount(updatedEndpoint.getRetryCount());
        existingEndpoint.setActive(updatedEndpoint.getActive());

        ServiceEndpoint savedEndpoint = serviceEndpointRepository.save(existingEndpoint);
        log.info("Successfully updated endpoint: {} {}", savedEndpoint.getHttpMethod(), savedEndpoint.getEndpointPath());

        return savedEndpoint;
    }

    /**
     * Delete an endpoint
     * @param endpointId The endpoint ID
     */
    @Transactional
    public void deleteEndpoint(UUID endpointId) {
        log.info("Deleting endpoint ID: {}", endpointId);

        ServiceEndpoint endpoint = serviceEndpointRepository.findById(endpointId)
                .orElseThrow(() -> new ServiceNotFoundException("Endpoint not found with ID: " + endpointId));

        serviceEndpointRepository.delete(endpoint);
        log.info("Successfully deleted endpoint: {} {}", endpoint.getHttpMethod(), endpoint.getEndpointPath());
    }

    /**
     * Create a new environment URL for a service
     * @param serviceId The service ID
     * @param environmentUrl The environment URL to create
     * @return The created environment URL
     */
    @Transactional
    public ServiceEnvironmentUrl createEnvironmentUrl(UUID serviceId, ServiceEnvironmentUrl environmentUrl) {
        log.info("Creating environment URL for service ID: {} in environment: {}",
                serviceId, environmentUrl.getEnvironment());

        ServiceRegistry service = serviceRegistryRepository.findById(serviceId)
                .orElseThrow(() -> new ServiceNotFoundException(serviceId));

        environmentUrl.setService(service);
        ServiceEnvironmentUrl savedUrl = serviceEnvironmentUrlRepository.save(environmentUrl);

        log.info("Successfully created environment URL for {} environment", savedUrl.getEnvironment());
        return savedUrl;
    }

    /**
     * Get all environment URLs for a service
     * @param serviceId The service ID
     * @return List of environment URLs
     */
    public List<ServiceEnvironmentUrl> getEnvironmentUrls(UUID serviceId) {
        log.debug("Fetching environment URLs for service ID: {}", serviceId);

        if (!serviceRegistryRepository.existsById(serviceId)) {
            throw new ServiceNotFoundException(serviceId);
        }

        return serviceEnvironmentUrlRepository.findByServiceId(serviceId);
    }

    /**
     * Get environment URL by ID
     * @param urlId The environment URL ID
     * @return The environment URL
     */
    public ServiceEnvironmentUrl getEnvironmentUrlById(UUID urlId) {
        log.debug("Fetching environment URL by ID: {}", urlId);
        return serviceEnvironmentUrlRepository.findById(urlId)
                .orElseThrow(() -> new ServiceNotFoundException("Environment URL not found with ID: " + urlId));
    }

    /**
     * Update an existing environment URL
     * @param urlId The environment URL ID
     * @param updatedUrl The updated environment URL data
     * @return The updated environment URL
     */
    @Transactional
    public ServiceEnvironmentUrl updateEnvironmentUrl(UUID urlId, ServiceEnvironmentUrl updatedUrl) {
        log.info("Updating environment URL ID: {}", urlId);

        ServiceEnvironmentUrl existingUrl = serviceEnvironmentUrlRepository.findById(urlId)
                .orElseThrow(() -> new ServiceNotFoundException("Environment URL not found with ID: " + urlId));

        // Update fields
        existingUrl.setEnvironment(updatedUrl.getEnvironment());
        existingUrl.setBaseUrl(updatedUrl.getBaseUrl());
        existingUrl.setPriority(updatedUrl.getPriority());
        existingUrl.setActive(updatedUrl.getActive());

        ServiceEnvironmentUrl savedUrl = serviceEnvironmentUrlRepository.save(existingUrl);
        log.info("Successfully updated environment URL for {} environment", savedUrl.getEnvironment());

        return savedUrl;
    }

    /**
     * Delete an environment URL
     * @param urlId The environment URL ID
     */
    @Transactional
    public void deleteEnvironmentUrl(UUID urlId) {
        log.info("Deleting environment URL ID: {}", urlId);

        ServiceEnvironmentUrl url = serviceEnvironmentUrlRepository.findById(urlId)
                .orElseThrow(() -> new ServiceNotFoundException("Environment URL not found with ID: " + urlId));

        serviceEnvironmentUrlRepository.delete(url);
        log.info("Successfully deleted environment URL for {} environment", url.getEnvironment());
    }
}
