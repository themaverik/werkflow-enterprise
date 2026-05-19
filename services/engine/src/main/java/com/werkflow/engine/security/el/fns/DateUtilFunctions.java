package com.werkflow.engine.security.el.fns;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Static date/time utility functions exposed to EL authors via the {@code dateUtil.*} prefix.
 *
 * <p>Per audit doc {@code EL-Expression-Security.md} P1-5 (task B-2): safe, author-facing
 * functions that replace ad-hoc Java method invocations in process expressions with a controlled,
 * auditable surface.
 *
 * <p><strong>Date-type decision:</strong> All methods use {@link OffsetDateTime} (java.time).
 * This matches the engine's canonical date representation used throughout the engine module
 * ({@code ProcessAuditLog.timestamp}, {@code SetVariablesDelegate}, {@code ConnectorDelegateBase}).
 * Flowable 7.x serialises {@code OffsetDateTime} process variables natively.
 * {@code java.util.Date} is deliberately avoided — it is mutable and lacks time-zone semantics.
 *
 * <p>All methods are {@code public static} (required by the Flowable 7.2
 * {@code FlowableFunctionDelegate} SPI). All methods are null-safe: a {@code null} argument
 * throws {@link IllegalArgumentException} with a clear message rather than
 * propagating a {@code NullPointerException}.
 */
public class DateUtilFunctions {

    private DateUtilFunctions() {
        // static-only utility class
    }

    /**
     * Returns the current timestamp at UTC offset.
     *
     * @return current {@link OffsetDateTime} at UTC
     */
    public static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    /**
     * Parses a date string using the given pattern.
     *
     * @param value   the date string to parse; must not be null or blank
     * @param pattern the {@link DateTimeFormatter} pattern (e.g. {@code "yyyy-MM-dd'T'HH:mm:ssXXX"});
     *                must not be null or blank
     * @return the parsed {@link OffsetDateTime}
     * @throws IllegalArgumentException if either argument is null/blank or the text cannot be parsed
     */
    public static OffsetDateTime parse(String value, String pattern) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("dateUtil.parse: 'value' must not be null or blank");
        }
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("dateUtil.parse: 'pattern' must not be null or blank");
        }
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            return OffsetDateTime.parse(value, formatter);
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            throw new IllegalArgumentException(
                    "dateUtil.parse: cannot parse '" + value + "' with pattern '" + pattern + "': " + ex.getMessage(),
                    ex);
        }
    }

    /**
     * Formats a date to a string using the given pattern.
     *
     * @param date    the {@link OffsetDateTime} to format; must not be null
     * @param pattern the {@link DateTimeFormatter} pattern; must not be null or blank
     * @return the formatted date string
     * @throws IllegalArgumentException if {@code date} is null or {@code pattern} is null/blank
     */
    public static String format(OffsetDateTime date, String pattern) {
        if (date == null) {
            throw new IllegalArgumentException("dateUtil.format: 'date' must not be null");
        }
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("dateUtil.format: 'pattern' must not be null or blank");
        }
        try {
            return date.format(DateTimeFormatter.ofPattern(pattern));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "dateUtil.format: invalid pattern '" + pattern + "': " + ex.getMessage(), ex);
        }
    }

    /**
     * Returns a new date shifted by the given number of days (negative to subtract).
     *
     * @param date the base {@link OffsetDateTime}; must not be null
     * @param days number of days to add (may be negative)
     * @return new {@link OffsetDateTime} shifted by {@code days}
     * @throws IllegalArgumentException if {@code date} is null
     */
    public static OffsetDateTime addDays(OffsetDateTime date, int days) {
        if (date == null) {
            throw new IllegalArgumentException("dateUtil.addDays: 'date' must not be null");
        }
        return date.plusDays(days);
    }
}
