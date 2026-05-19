package com.werkflow.engine.security.el.fns;

/**
 * Static math utility functions exposed to EL authors via the {@code mathUtil.*} prefix.
 *
 * <p>Per audit doc {@code EL-Expression-Security.md} P1-5 (task B-2): safe, author-facing
 * functions that replace ad-hoc Java method invocations in process expressions with a controlled,
 * auditable surface.
 *
 * <p>All methods are {@code public static} (required by the Flowable 7.2
 * {@code FlowableFunctionDelegate} SPI). Primitive {@code double} is used throughout —
 * BigDecimal is not used because the engine's EL context does not require arbitrary-precision
 * arithmetic and primitive arithmetic avoids the construction overhead of BigDecimal for
 * the simple rounding and comparison operations process authors need.
 */
public class MathUtilFunctions {

    private MathUtilFunctions() {
        // static-only utility class
    }

    /**
     * Rounds a value to the nearest whole number (half-up).
     *
     * @param value the value to round
     * @return rounded value as {@code double}
     */
    public static double round(double value) {
        return Math.round(value);
    }

    /**
     * Returns the absolute value.
     *
     * @param value the input value
     * @return absolute value
     */
    public static double abs(double value) {
        return Math.abs(value);
    }

    /**
     * Returns the smaller of two values.
     *
     * @param a first value
     * @param b second value
     * @return the minimum of {@code a} and {@code b}
     */
    public static double min(double a, double b) {
        return Math.min(a, b);
    }

    /**
     * Returns the larger of two values.
     *
     * @param a first value
     * @param b second value
     * @return the maximum of {@code a} and {@code b}
     */
    public static double max(double a, double b) {
        return Math.max(a, b);
    }
}
