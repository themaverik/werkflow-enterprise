package com.werkflow.engine.action;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantEndpointResolverTest {

    @Mock private RestTemplate adminTemplate;

    private TenantEndpointResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new TenantEndpointResolver(
            adminTemplate,
            "http://admin:8083",
            "development",
            Map.of("procurement", "http://procurement:8085", "inventory", "http://inventory:8084"),
            60L,
            500L
        );
    }

    @Test
    void resolve_returnsAdminBaseUrlWhenFound() {
        when(adminTemplate.getForObject(anyString(), eq(String.class)))
            .thenReturn("http://tenant-a-procurement:9090");

        String url = resolver.resolve("tenant-a", "procurement");

        assertThat(url).isEqualTo("http://tenant-a-procurement:9090");
    }

    @Test
    void resolve_fallsBackToGlobalConfigWhenAdminReturnsNull() {
        when(adminTemplate.getForObject(anyString(), eq(String.class)))
            .thenReturn(null);

        String url = resolver.resolve("tenant-a", "procurement");

        assertThat(url).isEqualTo("http://procurement:8085");
    }

    @Test
    void resolve_fallsBackToGlobalConfigWhenAdminThrows() {
        when(adminTemplate.getForObject(anyString(), eq(String.class)))
            .thenThrow(new RestClientException("timeout"));

        String url = resolver.resolve("tenant-a", "procurement");

        assertThat(url).isEqualTo("http://procurement:8085");
    }

    @Test
    void resolve_throwsWhenNeitherAdminNorGlobalFound() {
        when(adminTemplate.getForObject(anyString(), eq(String.class)))
            .thenReturn(null);

        assertThatThrownBy(() -> resolver.resolve("tenant-a", "unknown-connector"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("unknown-connector");
    }

    @Test
    void resolve_cachesPreviousResult() {
        when(adminTemplate.getForObject(anyString(), eq(String.class)))
            .thenReturn("http://cached-url");

        String first  = resolver.resolve("tenant-a", "procurement");
        String second = resolver.resolve("tenant-a", "procurement");

        assertThat(first).isEqualTo("http://cached-url");
        assertThat(second).isEqualTo("http://cached-url");
        verify(adminTemplate, times(1)).getForObject(anyString(), eq(String.class));
    }

    @Test
    void invalidateCache_clearsEntry() {
        when(adminTemplate.getForObject(anyString(), eq(String.class)))
            .thenReturn("http://cached-url");

        resolver.resolve("tenant-a", "procurement");
        resolver.invalidate("tenant-a", "procurement");
        resolver.resolve("tenant-a", "procurement");

        verify(adminTemplate, times(2)).getForObject(anyString(), eq(String.class));
    }

    @Test
    void resolve_fallsBackToGlobalConfigWhenAdminReturnsBlank() {
        when(adminTemplate.getForObject(anyString(), eq(String.class)))
            .thenReturn("   ");

        String url = resolver.resolve("tenant-a", "procurement");

        assertThat(url).isEqualTo("http://procurement:8085");
    }

    @Test
    void resolve_throwsOnNullTenantCode() {
        assertThatThrownBy(() -> resolver.resolve(null, "procurement"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void resolve_throwsOnNullConnectorKey() {
        assertThatThrownBy(() -> resolver.resolve("tenant-a", null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void invalidateCache_returnsRefreshedValueAfterInvalidation() {
        when(adminTemplate.getForObject(anyString(), eq(String.class)))
            .thenReturn("http://v1-url")
            .thenReturn("http://v2-url");

        String first = resolver.resolve("tenant-a", "procurement");
        resolver.invalidate("tenant-a", "procurement");
        String second = resolver.resolve("tenant-a", "procurement");

        assertThat(first).isEqualTo("http://v1-url");
        assertThat(second).isEqualTo("http://v2-url");
    }
}
