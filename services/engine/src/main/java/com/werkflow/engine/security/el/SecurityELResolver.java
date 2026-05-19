package com.werkflow.engine.security.el;

import com.fasterxml.jackson.databind.JsonNode;
import org.flowable.common.engine.impl.javax.el.ELContext;
import org.flowable.common.engine.impl.javax.el.ELResolver;

import java.beans.FeatureDescriptor;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Blanket method-invocation and POJO-reflection guard for the Flowable EL resolver chain.
 *
 * <p>Installed at position 2 (via {@code addPreDefaultResolver}) in
 * {@link RestrictedExpressionManager}, ahead of {@code BeanELResolver} but after the
 * variable resolver. Per audit doc {@code EL-Expression-Security.md §3.3/§3.4}:
 * <ul>
 *   <li>All {@code ELResolver.invoke} calls (method invocations on any base object) are
 *       blanket-blocked. Legitimate utility functions reach the evaluator via the
 *       {@link org.flowable.common.engine.impl.el.FlowableFunctionResolver} (FunctionMapper
 *       path), never via {@code invoke}.</li>
 *   <li>Property reads on POJO/bean types are blocked. {@link JsonNode},
 *       {@link Map}, {@link List}, and array bases are deferred to the downstream
 *       resolvers that handle them correctly.</li>
 *   <li>A {@link ThreadLocal} DMN-mode flag lets the task-D DMN interceptor bypass this
 *       resolver for FEEL expressions, which are evaluated by a separate evaluator.</li>
 * </ul>
 *
 * <p>Denial events are best-effort recorded by {@link ExpressionAuditLogger} before the
 * {@link WerkflowExpressionEvaluationException} is thrown. An audit-log write failure
 * never suppresses the security denial.
 */
public class SecurityELResolver extends ELResolver {

    /**
     * DMN mode flag. When {@code true}, this resolver defers on all calls so the
     * FEEL evaluator handles the expression unmodified. Set and cleared by the DMN
     * command interceptor introduced in task D.
     */
    static final ThreadLocal<Boolean> dmnMode = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * Synthetic site identifier used for audit log entries emitted from this resolver.
     * A per-BPMN site ID requires execution context not available at the resolver level;
     * forensic context is enriched at the delegate layer (tasks C/D).
     */
    private static final String RESOLVER_SITE_ID = "el-evaluation";

    private final ExpressionAuditLogger auditLogger;

    /**
     * Constructs the resolver with its required audit logger.
     *
     * @param auditLogger logger for recording denial events; must not be null
     */
    public SecurityELResolver(ExpressionAuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    // -------------------------------------------------------------------------
    // ThreadLocal accessors (package-private; used by task-D interceptor)
    // -------------------------------------------------------------------------

    /** Activates DMN pass-through mode on the current thread. */
    public static void enterDmnMode() {
        dmnMode.set(Boolean.TRUE);
    }

    /** Deactivates DMN pass-through mode on the current thread. */
    public static void exitDmnMode() {
        dmnMode.remove();
    }

    /** Returns {@code true} if the current thread is in DMN pass-through mode. */
    public static boolean isInDmnMode() {
        return Boolean.TRUE.equals(dmnMode.get());
    }

    // -------------------------------------------------------------------------
    // ELResolver — getValue
    // -------------------------------------------------------------------------

    /**
     * Defers for safe base types (null, {@link JsonNode}, {@link Map}, {@link List},
     * arrays). Blocks property reads on POJO/bean bases with {@code EL_DENY_REFLECTION}.
     *
     * <p>Per audit doc {@code EL-Expression-Security.md §3.3}.
     */
    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        if (isInDmnMode()) {
            return null; // defer — FEEL path handles it; do NOT set resolved
        }
        if (base == null) {
            return null; // defer to position-1 variable resolver
        }
        if (isSafeBase(base)) {
            return null; // defer to downstream resolvers (JsonNode / Map / List / array)
        }
        // bean-like POJO — deny
        deny(ExpressionErrorCode.EL_DENY_REFLECTION, context, base, property);
        return null; // never reached
    }

    // -------------------------------------------------------------------------
    // ELResolver — invoke (blanket block)
    // -------------------------------------------------------------------------

    /**
     * Blocks ALL method invocations on any non-null base. Legitimate EL functions
     * arrive via the FunctionMapper, not via this path.
     *
     * <p>Per audit doc {@code EL-Expression-Security.md §3.4}.
     */
    @Override
    public Object invoke(ELContext context, Object base, Object method,
                         Class<?>[] paramTypes, Object[] params) {
        if (isInDmnMode()) {
            return null;
        }
        if (base == null) {
            return null;
        }
        deny(ExpressionErrorCode.EL_DENY_REFLECTION, context, base, method);
        return null; // never reached
    }

    // -------------------------------------------------------------------------
    // ELResolver — getType
    // -------------------------------------------------------------------------

    /**
     * Defers for safe bases; blocks type resolution on POJO bases (consistent with
     * {@link #getValue}).
     */
    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
        if (isInDmnMode()) {
            return null;
        }
        if (base == null) {
            return null;
        }
        if (isSafeBase(base)) {
            return null;
        }
        deny(ExpressionErrorCode.EL_DENY_REFLECTION, context, base, property);
        return null; // never reached
    }

    // -------------------------------------------------------------------------
    // ELResolver — setValue
    // -------------------------------------------------------------------------

    /**
     * Defers for safe bases; blocks value writes on POJO bases with
     * {@code EL_DENY_REFLECTION}.
     */
    @Override
    public void setValue(ELContext context, Object base, Object property, Object value) {
        if (isInDmnMode()) {
            return;
        }
        if (base == null) {
            return;
        }
        if (isSafeBase(base)) {
            return;
        }
        deny(ExpressionErrorCode.EL_DENY_REFLECTION, context, base, property);
    }

    // -------------------------------------------------------------------------
    // ELResolver — isReadOnly
    // -------------------------------------------------------------------------

    /**
     * Always returns {@code true}. This resolver never permits writes; downstream
     * resolvers that accept the base will apply their own read-only logic.
     */
    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        return true;
    }

    // -------------------------------------------------------------------------
    // ELResolver — no-op metadata
    // -------------------------------------------------------------------------

    /** Returns {@code null} — this resolver does not provide property type metadata. */
    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        return null;
    }

    /** Returns {@code null} — this resolver does not enumerate feature descriptors. */
    @Override
    public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
        return null;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} for base types that downstream resolvers handle safely:
     * {@link JsonNode}, {@link Map}, {@link List}, and arrays. These do not expose
     * arbitrary class reflection paths.
     */
    private static boolean isSafeBase(Object base) {
        return base instanceof JsonNode
                || base instanceof Map
                || base instanceof List
                || base.getClass().isArray();
    }

    /**
     * Records the denial in the audit log (best-effort) then throws.
     * The audit-log call is wrapped so a write failure never masks the security denial.
     */
    private void deny(ExpressionErrorCode code, ELContext context, Object base, Object property) {
        try {
            String expressionText = base.getClass().getSimpleName()
                    + "." + (property != null ? property.toString() : "<unknown>");
            auditLogger.recordDenial(
                    code,
                    RESOLVER_SITE_ID,
                    expressionText,
                    null, null, null, null,
                    Map.of());
        } catch (Exception auditEx) {
            // audit failure is operational — do not let it suppress the security denial
        }
        throw new WerkflowExpressionEvaluationException(code, RESOLVER_SITE_ID);
    }
}
