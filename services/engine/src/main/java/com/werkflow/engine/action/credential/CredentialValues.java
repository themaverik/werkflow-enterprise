package com.werkflow.engine.action.credential;

import java.util.HashMap;
import java.util.Map;

/**
 * Typed envelope for a resolved set of credential key/value pairs.
 *
 * <p>The raw map is defensively copied at construction time so callers cannot
 * mutate the values after wrapping.
 *
 * <p>When a key is absent {@link #getString}, {@link #getInt}, and {@link #getBool}
 * return {@code null} — no fallback magic. Missing keys surface as {@code null}
 * to allow callers (e.g. {@link CredentialType#validate}) to distinguish "present
 * but blank" from "not provided at all".
 *
 * @param raw defensive copy of the raw credential map
 */
public record CredentialValues(Map<String, Object> raw) {

    /**
     * Defensively copies the supplied map so external mutation has no effect.
     */
    public CredentialValues(Map<String, Object> raw) {
        this.raw = raw == null ? Map.of() : Map.copyOf(raw);
    }

    /**
     * Returns the value for {@code key} as a {@link String}, or {@code null} if absent.
     * If the stored value is not a String it is converted via {@link Object#toString()}.
     */
    public String getString(String key) {
        Object value = raw.get(key);
        return value == null ? null : value.toString();
    }

    /**
     * Returns the value for {@code key} as an {@link Integer}, or {@code null} if absent.
     * Supports stored types of {@link Integer} and {@link String} (parsed).
     *
     * @throws NumberFormatException if the stored string cannot be parsed as an integer
     */
    public Integer getInt(String key) {
        Object value = raw.get(key);
        if (value == null) return null;
        if (value instanceof Integer i) return i;
        return Integer.parseInt(value.toString());
    }

    /**
     * Returns the value for {@code key} as a {@link Boolean}, or {@code null} if absent.
     * Supports stored types of {@link Boolean} and {@link String} (parsed via
     * {@link Boolean#parseBoolean}).
     */
    public Boolean getBool(String key) {
        Object value = raw.get(key);
        if (value == null) return null;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(value.toString());
    }

    /**
     * Convenience factory for building a {@link CredentialValues} instance in tests
     * or resolve implementations without constructing a mutable map first.
     */
    public static CredentialValues of(Map<String, Object> entries) {
        return new CredentialValues(new HashMap<>(entries));
    }
}
