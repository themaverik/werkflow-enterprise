package com.werkflow.engine.security.el.fns;

/**
 * Static string utility functions exposed to EL authors via the {@code stringUtil.*} prefix.
 *
 * <p>Per audit doc {@code EL-Expression-Security.md} P1-5 (task B-2): safe, author-facing
 * functions that replace ad-hoc Java method invocations in process expressions with a controlled,
 * auditable surface.
 *
 * <p>All methods are {@code public static} (required by the Flowable 7.2
 * {@code FlowableFunctionDelegate} SPI). All methods are null-safe: a {@code null} input
 * returns the null-identity result for the operation (empty string or the original value
 * where replacement has nothing to act on), avoiding silent NPEs in process expressions.
 */
public class StringUtilFunctions {

    private StringUtilFunctions() {
        // static-only utility class
    }

    /**
     * Converts a string to upper case.
     *
     * @param value the input string; returns empty string if null
     * @return upper-cased string
     */
    public static String upper(String value) {
        if (value == null) {
            return "";
        }
        return value.toUpperCase();
    }

    /**
     * Converts a string to lower case.
     *
     * @param value the input string; returns empty string if null
     * @return lower-cased string
     */
    public static String lower(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase();
    }

    /**
     * Trims leading and trailing whitespace from a string.
     *
     * @param value the input string; returns empty string if null
     * @return trimmed string
     */
    public static String trim(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    /**
     * Replaces all occurrences of {@code target} with {@code replacement} in {@code input}.
     *
     * @param input       the source string; returns empty string if null
     * @param target      the literal substring to replace; no-op if null or empty
     * @param replacement the replacement string; treated as empty string if null
     * @return the result of the replacement, or {@code input} unchanged if {@code target}
     *         is null or empty
     */
    public static String replace(String input, String target, String replacement) {
        if (input == null) {
            return "";
        }
        if (target == null || target.isEmpty()) {
            return input;
        }
        String safeReplacement = replacement == null ? "" : replacement;
        return input.replace(target, safeReplacement);
    }
}
