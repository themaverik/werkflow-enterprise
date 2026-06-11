package com.werkflow.engine.webhook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

/**
 * Pluggable HMAC verifier for inbound webhooks.
 *
 * <p>Supported strategies (set in ConnectorDefinition webhook.hmac.strategy):
 * <ul>
 *   <li>{@code none}    — no verification; accept any payload</li>
 *   <li>{@code generic} — HMAC-SHA256(secret, rawBody); header contains hex digest</li>
 *   <li>{@code github}  — X-Hub-Signature-256: sha256=&lt;hex&gt;</li>
 *   <li>{@code stripe}  — X-Stripe-Signature: t=&lt;ts&gt;,v1=&lt;hex&gt;;
 *                         verifies HMAC-SHA256(secret, ts + "." + rawBody)</li>
 * </ul>
 */
@Slf4j
@Component
public class HmacVerifier {

    private static final String ALGO = "HmacSHA256";
    private static final long STRIPE_TIMESTAMP_TOLERANCE_SECONDS = 300;

    /**
     * @param strategy     hmac.strategy from connector definition
     * @param headerName   hmac.headerName from connector definition
     * @param secret       resolved shared secret
     * @param rawBody      unmodified request body bytes
     * @param headers      request headers (lowercase keys expected)
     * @return true if signature is valid (or strategy is none), false otherwise
     */
    public boolean verify(String strategy, String headerName, String secret,
                          byte[] rawBody, Map<String, String> headers) {
        if (strategy == null || strategy.isBlank() || "none".equalsIgnoreCase(strategy)) {
            return true;
        }
        if (secret == null || secret.isBlank()) {
            log.warn("HmacVerifier: strategy={} but no secret configured — rejecting", strategy);
            return false;
        }

        return switch (strategy.toLowerCase()) {
            case "github" -> verifyGithub(secret, rawBody, headers);
            case "stripe" -> verifyStripe(secret, rawBody, headers);
            default       -> verifyGeneric(strategy, headerName, secret, rawBody, headers);
        };
    }

    // -------------------------------------------------------------------------

    private boolean verifyGithub(String secret, byte[] rawBody, Map<String, String> headers) {
        String sig = headers.get("x-hub-signature-256");
        if (sig == null || !sig.startsWith("sha256=")) {
            log.warn("HmacVerifier[github]: missing or malformed X-Hub-Signature-256 header");
            return false;
        }
        String expected = "sha256=" + hmacHex(secret, rawBody);
        return constantTimeEquals(expected, sig);
    }

    private boolean verifyStripe(String secret, byte[] rawBody, Map<String, String> headers) {
        // Stripe: X-Stripe-Signature: t=<timestamp>,v1=<hex>
        String sig = headers.get("x-stripe-signature");
        if (sig == null) {
            log.warn("HmacVerifier[stripe]: missing X-Stripe-Signature header");
            return false;
        }
        String timestamp = null;
        String v1 = null;
        for (String part : sig.split(",")) {
            if (part.startsWith("t=")) timestamp = part.substring(2);
            if (part.startsWith("v1=")) v1 = part.substring(3);
        }
        if (timestamp == null || v1 == null) {
            log.warn("HmacVerifier[stripe]: malformed X-Stripe-Signature — missing t= or v1= field");
            return false;
        }
        try {
            long ts = Long.parseLong(timestamp);
            long nowSeconds = Instant.now().getEpochSecond();
            if (Math.abs(nowSeconds - ts) > STRIPE_TIMESTAMP_TOLERANCE_SECONDS) {
                log.warn("HmacVerifier[stripe]: timestamp too old or too new: {}", timestamp);
                return false;
            }
        } catch (NumberFormatException e) {
            log.warn("HmacVerifier[stripe]: non-numeric timestamp: {}", timestamp);
            return false;
        }
        byte[] payload = (timestamp + "." + new String(rawBody, StandardCharsets.UTF_8))
                .getBytes(StandardCharsets.UTF_8);
        String expected = hmacHex(secret, payload);
        return constantTimeEquals(expected, v1);
    }

    private boolean verifyGeneric(String strategy, String headerName, String secret,
                                  byte[] rawBody, Map<String, String> headers) {
        String key = headerName != null ? headerName.toLowerCase() : "x-werkflow-signature";
        String sig = headers.get(key);
        if (sig == null) {
            log.warn("HmacVerifier[{}]: missing signature header '{}'", strategy, key);
            return false;
        }
        String expected = hmacHex(secret, rawBody);
        // strip any prefix like "sha256="
        String actual = sig.contains("=") ? sig.substring(sig.indexOf('=') + 1) : sig;
        return constantTimeEquals(expected, actual);
    }

    // -------------------------------------------------------------------------

    private String hmacHex(String secret, byte[] data) {
        try {
            Mac mac = Mac.getInstance(ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGO));
            return HexFormat.of().formatHex(mac.doFinal(data));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacVerifier: failed to compute HMAC", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
