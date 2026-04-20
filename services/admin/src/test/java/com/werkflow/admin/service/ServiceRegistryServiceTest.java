package com.werkflow.admin.service;

import com.werkflow.admin.entity.serviceregistry.*;
import com.werkflow.admin.exception.DuplicateServiceException;
import com.werkflow.admin.exception.EnvironmentNotConfiguredException;
import com.werkflow.admin.exception.ServiceNotFoundException;
import com.werkflow.admin.repository.serviceregistry.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ServiceRegistryService
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ServiceRegistryService Unit Tests")
class ServiceRegistryServiceTest {

    @Mock
    private ServiceRegistryRepository serviceRegistryRepository;

    @Mock
    private ServiceEndpointRepository serviceEndpointRepository;

    @Mock
    private ServiceEnvironmentUrlRepository serviceEnvironmentUrlRepository;

    @Mock
    private ServiceHealthCheckRepository serviceHealthCheckRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private ServiceRegistryService serviceRegistryService;

    private ServiceRegistry testService;
    private ServiceEnvironmentUrl testEnvironmentUrl;
    private UUID testServiceId;

    @BeforeEach
    void setUp() {
        testServiceId = UUID.randomUUID();

        testService = ServiceRegistry.builder()
            .id(testServiceId)
            .serviceName("test-service")
            .displayName("Test Service")
            .description("A test service")
            .serviceType(ServiceType.INTERNAL)
            .basePath("/api/v1")
            .version("1.0.0")
            .healthCheckUrl("/actuator/health")
            .healthStatus(HealthStatus.UNKNOWN)
            .active(true)
            .build();

        testEnvironmentUrl = ServiceEnvironmentUrl.builder()
            .id(UUID.randomUUID())
            .service(testService)
            .environment(Environment.development)
            .baseUrl("http://localhost:8080")
            .priority(1)
            .active(true)
            .build();
    }

    @Test
    @DisplayName("Should successfully register a new service")
    void testRegisterService_Success() {
        // Arrange
        when(serviceRegistryRepository.existsByServiceName(testService.getServiceName())).thenReturn(false);
        when(serviceRegistryRepository.save(testService)).thenReturn(testService);

        // Act
        ServiceRegistry result = serviceRegistryService.registerService(testService);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getServiceName()).isEqualTo("test-service");
        verify(serviceRegistryRepository).existsByServiceName("test-service");
        verify(serviceRegistryRepository).save(testService);
    }

    @Test
    @DisplayName("Should throw DuplicateServiceException when registering duplicate service")
    void testRegisterService_DuplicateService() {
        // Arrange
        when(serviceRegistryRepository.existsByServiceName(testService.getServiceName())).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> serviceRegistryService.registerService(testService))
            .isInstanceOf(DuplicateServiceException.class)
            .hasMessageContaining("test-service");

        verify(serviceRegistryRepository).existsByServiceName("test-service");
        verify(serviceRegistryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should successfully resolve service URL")
    void testResolveServiceUrl_Success() {
        // Arrange
        String serviceName = "test-service";
        Environment environment = Environment.development;

        when(serviceRegistryRepository.findByServiceName(serviceName)).thenReturn(Optional.of(testService));
        when(serviceEnvironmentUrlRepository.findFirstByServiceIdAndEnvironmentAndActiveTrueOrderByPriorityAsc(
            testServiceId, environment)).thenReturn(Optional.of(testEnvironmentUrl));

        // Act
        String result = serviceRegistryService.resolveServiceUrl(serviceName, environment);

        // Assert
        assertThat(result).isEqualTo("http://localhost:8080/api/v1");
        verify(serviceRegistryRepository).findByServiceName(serviceName);
        verify(serviceEnvironmentUrlRepository).findFirstByServiceIdAndEnvironmentAndActiveTrueOrderByPriorityAsc(
            testServiceId, environment);
    }

    @Test
    @DisplayName("Should throw ServiceNotFoundException when service not found")
    void testResolveServiceUrl_ServiceNotFound() {
        // Arrange
        String serviceName = "non-existent-service";
        Environment environment = Environment.development;

        when(serviceRegistryRepository.findByServiceName(serviceName)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> serviceRegistryService.resolveServiceUrl(serviceName, environment))
            .isInstanceOf(ServiceNotFoundException.class)
            .hasMessageContaining("non-existent-service");

        verify(serviceRegistryRepository).findByServiceName(serviceName);
        verify(serviceEnvironmentUrlRepository, never()).findFirstByServiceIdAndEnvironmentAndActiveTrueOrderByPriorityAsc(any(), any());
    }

    @Test
    @DisplayName("Should throw EnvironmentNotConfiguredException when environment not configured")
    void testResolveServiceUrl_EnvironmentNotConfigured() {
        // Arrange
        String serviceName = "test-service";
        Environment environment = Environment.production;

        when(serviceRegistryRepository.findByServiceName(serviceName)).thenReturn(Optional.of(testService));
        when(serviceEnvironmentUrlRepository.findFirstByServiceIdAndEnvironmentAndActiveTrueOrderByPriorityAsc(
            testServiceId, environment)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> serviceRegistryService.resolveServiceUrl(serviceName, environment))
            .isInstanceOf(EnvironmentNotConfiguredException.class)
            .hasMessageContaining("test-service")
            .hasMessageContaining("production");

        verify(serviceRegistryRepository).findByServiceName(serviceName);
        verify(serviceEnvironmentUrlRepository).findFirstByServiceIdAndEnvironmentAndActiveTrueOrderByPriorityAsc(
            testServiceId, environment);
    }

    @Test
    @DisplayName("Should successfully perform health check and mark service as HEALTHY")
    void testPerformHealthCheck_Healthy() {
        // Arrange
        Environment environment = Environment.development;
        String healthCheckUrl = "http://localhost:8080/actuator/health";

        when(serviceRegistryRepository.findById(testServiceId)).thenReturn(Optional.of(testService));
        when(serviceEnvironmentUrlRepository.findFirstByServiceIdAndEnvironmentAndActiveTrueOrderByPriorityAsc(
            testServiceId, environment)).thenReturn(Optional.of(testEnvironmentUrl));
        when(restTemplate.getForEntity(healthCheckUrl, String.class))
            .thenReturn(new ResponseEntity<>("{\"status\":\"UP\"}", HttpStatus.OK));
        when(serviceHealthCheckRepository.save(any(ServiceHealthCheck.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(serviceRegistryRepository.save(any(ServiceRegistry.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        ServiceHealthCheck result = serviceRegistryService.performHealthCheck(testServiceId, environment);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(HealthStatus.HEALTHY);
        assertThat(result.getResponseTimeMs()).isNotNull();
        assertThat(result.getResponseTimeMs()).isGreaterThanOrEqualTo(0);
        verify(serviceHealthCheckRepository).save(any(ServiceHealthCheck.class));
        verify(serviceRegistryRepository).save(any(ServiceRegistry.class));
    }

    @Test
    @DisplayName("Should mark service as UNHEALTHY when health check fails")
    void testPerformHealthCheck_Unhealthy() {
        // Arrange
        Environment environment = Environment.development;
        String healthCheckUrl = "http://localhost:8080/actuator/health";

        when(serviceRegistryRepository.findById(testServiceId)).thenReturn(Optional.of(testService));
        when(serviceEnvironmentUrlRepository.findFirstByServiceIdAndEnvironmentAndActiveTrueOrderByPriorityAsc(
            testServiceId, environment)).thenReturn(Optional.of(testEnvironmentUrl));
        when(restTemplate.getForEntity(healthCheckUrl, String.class))
            .thenThrow(new RestClientException("Connection refused"));
        when(serviceHealthCheckRepository.save(any(ServiceHealthCheck.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(serviceRegistryRepository.save(any(ServiceRegistry.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        ServiceHealthCheck result = serviceRegistryService.performHealthCheck(testServiceId, environment);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(HealthStatus.UNHEALTHY);
        assertThat(result.getErrorMessage()).contains("Connection refused");
        verify(serviceHealthCheckRepository).save(any(ServiceHealthCheck.class));
        verify(serviceRegistryRepository).save(any(ServiceRegistry.class));
    }

    @Test
    @DisplayName("Should successfully retrieve all active services")
    void testGetAllActiveServices_Success() {
        // Arrange
        List<ServiceRegistry> activeServices = List.of(testService);
        when(serviceRegistryRepository.findByActiveTrue()).thenReturn(activeServices);

        // Act
        List<ServiceRegistry> result = serviceRegistryService.getAllActiveServices();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getServiceName()).isEqualTo("test-service");
        verify(serviceRegistryRepository).findByActiveTrue();
    }

    @Test
    @DisplayName("Should successfully retrieve service by name")
    void testGetServiceByName_Success() {
        // Arrange
        String serviceName = "test-service";
        when(serviceRegistryRepository.findByServiceName(serviceName)).thenReturn(Optional.of(testService));

        // Act
        ServiceRegistry result = serviceRegistryService.getServiceByName(serviceName);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getServiceName()).isEqualTo(serviceName);
        verify(serviceRegistryRepository).findByServiceName(serviceName);
    }

    @Test
    @DisplayName("Should successfully retrieve service endpoints")
    void testGetServiceEndpoints_Success() {
        // Arrange
        ServiceEndpoint endpoint = ServiceEndpoint.builder()
            .id(UUID.randomUUID())
            .service(testService)
            .endpointPath("/budgets")
            .httpMethod(HttpMethod.GET)
            .description("Get budgets")
            .active(true)
            .build();

        when(serviceRegistryRepository.existsById(testServiceId)).thenReturn(true);
        when(serviceEndpointRepository.findByServiceIdAndActiveTrue(testServiceId))
            .thenReturn(List.of(endpoint));

        // Act
        List<ServiceEndpoint> result = serviceRegistryService.getServiceEndpoints(testServiceId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEndpointPath()).isEqualTo("/budgets");
        verify(serviceRegistryRepository).existsById(testServiceId);
        verify(serviceEndpointRepository).findByServiceIdAndActiveTrue(testServiceId);
    }

    @Test
    @DisplayName("Should throw ServiceNotFoundException when getting endpoints for non-existent service")
    void testGetServiceEndpoints_ServiceNotFound() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();
        when(serviceRegistryRepository.existsById(nonExistentId)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> serviceRegistryService.getServiceEndpoints(nonExistentId))
            .isInstanceOf(ServiceNotFoundException.class);

        verify(serviceRegistryRepository).existsById(nonExistentId);
        verify(serviceEndpointRepository, never()).findByServiceIdAndActiveTrue(any());
    }

    @Test
    @DisplayName("Should successfully update service health status")
    void testUpdateServiceHealthStatus_Success() {
        // Arrange
        HealthStatus newStatus = HealthStatus.HEALTHY;
        when(serviceRegistryRepository.findById(testServiceId)).thenReturn(Optional.of(testService));
        when(serviceRegistryRepository.save(any(ServiceRegistry.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        serviceRegistryService.updateServiceHealthStatus(testServiceId, newStatus);

        // Assert
        verify(serviceRegistryRepository).findById(testServiceId);
        verify(serviceRegistryRepository).save(any(ServiceRegistry.class));
    }
}
