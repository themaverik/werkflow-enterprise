package com.werkflow.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;

@Slf4j
@Component
public class SsrfGuard {

    public void validate(String rawUrl) {
        URL url;
        try {
            url = new URL(rawUrl);
        } catch (MalformedURLException e) {
            throw new SecurityException("Invalid URL format.");
        }

        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new SecurityException("Only https scheme is permitted. Got: " + url.getProtocol());
        }

        String host = url.getHost();
        try {
            InetAddress address = InetAddress.getByName(host);
            checkDeniedAddress(address);
        } catch (UnknownHostException e) {
            throw new SecurityException("Cannot resolve host.");
        }
    }

    /**
     * Validates a URL sourced from the connector registry (TenantServiceEndpoint).
     *
     * Registry URLs are admin-controlled and may point to internal services, so:
     * - HTTP scheme is permitted (internal services may not run TLS)
     * - Private/site-local IP ranges are permitted (172.16/12, 10.x, 192.168.x)
     *
     * The following are still denied:
     * - Loopback addresses (127.x, ::1)
     * - Link-local addresses (169.254.x, fe80::)
     * - AnyLocal (0.0.0.0)
     * - Unresolvable hostnames (fail-closed: unknown = denied)
     *
     * Note: DNS rebinding is not mitigated here — ensure the HTTP client does not
     * follow redirects and that connector registry entries are admin-audited.
     */
    public void validateExternal(String rawUrl) {
        URL url;
        try {
            url = new URL(rawUrl);
        } catch (MalformedURLException e) {
            throw new SecurityException("Invalid URL format.");
        }
        String host = url.getHost();
        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isAnyLocalAddress()) {
                throw new SecurityException("URL resolves to a denied loopback or link-local address.");
            }
        } catch (UnknownHostException e) {
            throw new SecurityException("Cannot resolve host.");
        }
    }

    private void checkDeniedAddress(InetAddress address) {
        if (address.isLoopbackAddress() || address.isLinkLocalAddress() ||
            address.isSiteLocalAddress() || address.isAnyLocalAddress()) {
            throw new SecurityException("URL resolves to a denied private/loopback address.");
        }
        byte[] raw = address.getAddress();
        if (raw.length == 4) {
            int first  = raw[0] & 0xFF;
            int second = raw[1] & 0xFF;
            if (first == 172 && second >= 16 && second <= 31) {
                throw new SecurityException("URL resolves to a denied private address (172.16/12).");
            }
        }
    }
}
