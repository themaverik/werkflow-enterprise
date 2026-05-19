package com.werkflow.engine.security.el;

import org.flowable.common.engine.api.delegate.FlowableFunctionDelegate;

import java.lang.reflect.Method;

/**
 * Generic {@link FlowableFunctionDelegate} implementation that holds a pre-resolved
 * {@link Method} reference.
 *
 * <p>Per audit doc {@code EL-Expression-Security.md} P1-5 (task B-2): avoids one
 * anonymous class per function — a single reusable record covers all utility function
 * registrations in {@link FunctionRegistry}.
 *
 * <p>The backing {@code method} must be {@code public static} on its declaring class.
 * {@link FunctionRegistry} resolves all method handles once at class-init and stores
 * them here; {@code functionMethod()} simply returns the pre-resolved reference with
 * no per-call reflection cost.
 *
 * @param prefix    the EL function namespace prefix (e.g. {@code "dateUtil"})
 * @param localName the EL function local name (e.g. {@code "now"})
 * @param method    the pre-resolved public static method backing this function
 */
public record SafeFunctionDelegate(
        String prefix,
        String localName,
        Method method
) implements FlowableFunctionDelegate {

    /**
     * Returns the EL namespace prefix.
     *
     * @return prefix string; never null
     */
    @Override
    public String prefix() {
        return prefix;
    }

    /**
     * Returns the EL local function name.
     *
     * @return local name string; never null
     */
    @Override
    public String localName() {
        return localName;
    }

    /**
     * Returns the pre-resolved static method. Never throws — the method was validated
     * at class-init time by {@link FunctionRegistry}.
     *
     * @return the resolved {@link Method}
     */
    @Override
    public Method functionMethod() {
        return method;
    }
}
