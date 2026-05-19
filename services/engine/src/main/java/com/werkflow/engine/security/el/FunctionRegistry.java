package com.werkflow.engine.security.el;

import com.werkflow.engine.security.el.fns.DateUtilFunctions;
import com.werkflow.engine.security.el.fns.MathUtilFunctions;
import com.werkflow.engine.security.el.fns.StringUtilFunctions;
import org.flowable.common.engine.api.delegate.FlowableFunctionDelegate;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Pre-built, immutable bundles of {@link FlowableFunctionDelegate} instances for use
 * by {@code RestrictedExpressionManager} (B-3).
 *
 * <p>Per audit doc {@code EL-Expression-Security.md} P1-5 (task B-2): bundles are
 * defined here so {@code RestrictedExpressionManager} can select the appropriate set
 * for a given expression context without re-resolving methods at runtime.
 *
 * <p>Three pre-built bundles are available:
 * <ul>
 *   <li>{@link #DATE} — date/time functions only ({@code dateUtil.*})</li>
 *   <li>{@link #DATE_STRING} — date/time + string functions ({@code dateUtil.*} + {@code stringUtil.*})</li>
 *   <li>{@link #DATE_STRING_MATH} — all functions ({@code dateUtil.*} + {@code stringUtil.*} + {@code mathUtil.*})</li>
 * </ul>
 *
 * <p>All {@link Method} handles are resolved once at class-load time via reflection.
 * A missing method is a compile-time regression, not a runtime condition — therefore
 * any {@link NoSuchMethodException} during initialisation is wrapped and re-thrown
 * as a {@link RuntimeException} to fail fast at class-load rather than producing a
 * half-initialised registry.
 */
public final class FunctionRegistry {

    /** Date/time functions only: {@code dateUtil.now}, {@code dateUtil.parse},
     *  {@code dateUtil.format}, {@code dateUtil.addDays}. */
    public static final List<FlowableFunctionDelegate> DATE;

    /** Date/time + string functions. */
    public static final List<FlowableFunctionDelegate> DATE_STRING;

    /** All available functions: date/time, string, and math. */
    public static final List<FlowableFunctionDelegate> DATE_STRING_MATH;

    static {
        try {
            // --- dateUtil functions ---
            SafeFunctionDelegate dateNow = fn("dateUtil", "now",
                    DateUtilFunctions.class.getMethod("now"));

            SafeFunctionDelegate dateParse = fn("dateUtil", "parse",
                    DateUtilFunctions.class.getMethod("parse", String.class, String.class));

            SafeFunctionDelegate dateFormat = fn("dateUtil", "format",
                    DateUtilFunctions.class.getMethod("format", OffsetDateTime.class, String.class));

            SafeFunctionDelegate dateAddDays = fn("dateUtil", "addDays",
                    DateUtilFunctions.class.getMethod("addDays", OffsetDateTime.class, int.class));

            // --- stringUtil functions ---
            SafeFunctionDelegate stringUpper = fn("stringUtil", "upper",
                    StringUtilFunctions.class.getMethod("upper", String.class));

            SafeFunctionDelegate stringLower = fn("stringUtil", "lower",
                    StringUtilFunctions.class.getMethod("lower", String.class));

            SafeFunctionDelegate stringTrim = fn("stringUtil", "trim",
                    StringUtilFunctions.class.getMethod("trim", String.class));

            SafeFunctionDelegate stringReplace = fn("stringUtil", "replace",
                    StringUtilFunctions.class.getMethod("replace", String.class, String.class, String.class));

            // --- mathUtil functions ---
            SafeFunctionDelegate mathRound = fn("mathUtil", "round",
                    MathUtilFunctions.class.getMethod("round", double.class));

            SafeFunctionDelegate mathAbs = fn("mathUtil", "abs",
                    MathUtilFunctions.class.getMethod("abs", double.class));

            SafeFunctionDelegate mathMin = fn("mathUtil", "min",
                    MathUtilFunctions.class.getMethod("min", double.class, double.class));

            SafeFunctionDelegate mathMax = fn("mathUtil", "max",
                    MathUtilFunctions.class.getMethod("max", double.class, double.class));

            List<SafeFunctionDelegate> dateFns = List.of(dateNow, dateParse, dateFormat, dateAddDays);
            List<SafeFunctionDelegate> stringFns = List.of(stringUpper, stringLower, stringTrim, stringReplace);
            List<SafeFunctionDelegate> mathFns = List.of(mathRound, mathAbs, mathMin, mathMax);

            DATE = List.copyOf(dateFns);

            DATE_STRING = List.copyOf(concat(dateFns, stringFns));

            DATE_STRING_MATH = List.copyOf(concat(concat(dateFns, stringFns), mathFns));

        } catch (NoSuchMethodException ex) {
            // A missing method is a build-time regression — fail immediately at class-load.
            throw new RuntimeException(
                    "FunctionRegistry: method resolution failed — check function signatures in util classes: "
                            + ex.getMessage(),
                    ex);
        }
    }

    private FunctionRegistry() {
        // static-only
    }

    private static SafeFunctionDelegate fn(String prefix, String localName, Method method) {
        return new SafeFunctionDelegate(prefix, localName, method);
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        List<T> result = new java.util.ArrayList<>(a.size() + b.size());
        result.addAll(a);
        result.addAll(b);
        return result;
    }
}
