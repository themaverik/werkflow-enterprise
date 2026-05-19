package com.werkflow.engine.security.el;

import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.common.engine.api.delegate.FlowableFunctionDelegate;
import org.flowable.engine.impl.el.ProcessExpressionManager;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Hardened {@link ProcessExpressionManager} that enforces parse-time expression limits and
 * installs {@link SecurityELResolver} ahead of the default resolver chain.
 *
 * <p>Implements the approved "Option 1" sub-manager design from audit doc
 * {@code EL-Expression-Security.md §4.1/§4.3}: one pre-built sub-manager per
 * {@link FunctionRegistry} bundle so function-scoped expressions are compiled once
 * through a manager that knows exactly which functions are permitted — without re-building
 * the chain at call time.
 *
 * <p>Two constructors enforce a strict shape:
 * <ul>
 *   <li><b>ROOT constructor</b> — called by Spring (task C). Owns the sub-manager map and
 *       installs a shared {@link SecurityELResolver} into its own chain.</li>
 *   <li><b>LEAF constructor</b> — private. Each sub-manager for a function bundle. Installs
 *       its own {@link SecurityELResolver} and registers the bundle via
 *       {@code setFunctionDelegates}. Does NOT build nested sub-managers (no recursion).</li>
 * </ul>
 *
 * <h3>Parse-time limits (applied in {@link #createExpression})</h3>
 * <ul>
 *   <li>Length: checked against {@link ExpressionLimitsConfig#getMaxLength()} — fast O(1).</li>
 *   <li>Nesting depth: heuristic — counts maximum {@code (} nesting depth in the text.
 *       The JUEL AST {@code ExpressionNode} is stored as a {@code private transient} field
 *       in {@code TreeValueExpression} with no public accessor; accessing it would require
 *       reflection into Flowable internals, which is explicitly prohibited by this audit.
 *       The parenthesis heuristic is a conservative upper-bound: it cannot undercount actual
 *       nesting depth (it may overcount for string literals containing parentheses, which is
 *       acceptable — the limit of 8 is well above any legitimate expression).</li>
 *   <li>Function calls: heuristic — counts each {@code (} preceded by an identifier
 *       character (every call site). Same rationale as depth: no clean AST access path
 *       without reflection.</li>
 * </ul>
 */
public class RestrictedExpressionManager extends ProcessExpressionManager {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /**
     * Number of {@link FunctionRegistry} bundles — sizes the sub-manager
     * {@code IdentityHashMap}, which is keyed by list reference identity so the three
     * bundle constants never collide under structural list equality.
     */
    private static final int BUNDLE_COUNT = 3;

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final ExpressionLimitsConfig limits;
    private final ExpressionAuditLogger auditLogger;

    /**
     * Non-null only on the ROOT instance. Each entry maps a {@link FunctionRegistry}
     * list constant (by reference identity) to its leaf sub-manager.
     */
    private final Map<List<FlowableFunctionDelegate>, RestrictedExpressionManager> subManagers;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * ROOT constructor — called by the Spring {@code FlowableConfig} bean (task C).
     *
     * <p>Installs {@link SecurityELResolver} into this manager's pre-default resolver list
     * and builds one leaf sub-manager per {@link FunctionRegistry} bundle.
     *
     * <p>{@code beans} must be the same {@code SpringBeanFactoryProxyMap} that
     * {@code SpringProcessEngineConfiguration.initBeans()} would install — ensuring
     * {@code ${delegateBean}} EL lookups resolve Spring beans identically to the default
     * {@code ProcessExpressionManager}. Flowable's {@code initExpressionManager()} does
     * NOT inject the beans map into a user-supplied manager; the caller must provide it.
     *
     * @param limits      parse-time hard limits; must not be null
     * @param auditLogger audit logger for denial events; must not be null
     * @param beans       Spring bean factory proxy map from the engine's ApplicationContext;
     *                    must not be null
     */
    public RestrictedExpressionManager(ExpressionLimitsConfig limits,
                                       ExpressionAuditLogger auditLogger,
                                       Map<Object, Object> beans) {
        super(Objects.requireNonNull(beans, "beans must not be null"));
        this.limits = limits;
        this.auditLogger = auditLogger;
        addPreDefaultResolver(new SecurityELResolver(auditLogger));

        Map<List<FlowableFunctionDelegate>, RestrictedExpressionManager> map =
                new IdentityHashMap<>(BUNDLE_COUNT);
        map.put(FunctionRegistry.DATE,
                new RestrictedExpressionManager(limits, auditLogger, beans, FunctionRegistry.DATE));
        map.put(FunctionRegistry.DATE_STRING,
                new RestrictedExpressionManager(limits, auditLogger, beans, FunctionRegistry.DATE_STRING));
        map.put(FunctionRegistry.DATE_STRING_MATH,
                new RestrictedExpressionManager(limits, auditLogger, beans, FunctionRegistry.DATE_STRING_MATH));
        this.subManagers = Collections.unmodifiableMap(map);
    }

    /**
     * LEAF constructor — private, used only by the ROOT constructor.
     *
     * <p>Installs {@link SecurityELResolver} and registers the given function bundle.
     * Does not build sub-managers to prevent infinite recursion.
     *
     * <p>Receives the same {@code beans} map as the ROOT so that function-scoped
     * {@code compileWithFunctions} expressions can resolve Spring beans.
     *
     * @param limits      parse-time hard limits
     * @param auditLogger audit logger for denial events
     * @param beans       Spring bean factory proxy map; forwarded from ROOT
     * @param bundle      the function bundle to register via {@code setFunctionDelegates}
     */
    private RestrictedExpressionManager(ExpressionLimitsConfig limits,
                                        ExpressionAuditLogger auditLogger,
                                        Map<Object, Object> beans,
                                        List<FlowableFunctionDelegate> bundle) {
        super(beans);
        this.limits = limits;
        this.auditLogger = auditLogger;
        this.subManagers = null; // leaf — no sub-managers
        addPreDefaultResolver(new SecurityELResolver(auditLogger));
        setFunctionDelegates(bundle);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Compiles {@code text} through the sub-manager pre-configured for {@code bundle}.
     *
     * <p>Parse-time limits are enforced by the leaf sub-manager's {@link #createExpression}
     * override — no duplication needed here.
     *
     * @param text   the EL expression text; must not be null
     * @param bundle one of {@link FunctionRegistry#DATE}, {@link FunctionRegistry#DATE_STRING},
     *               or {@link FunctionRegistry#DATE_STRING_MATH}
     * @return the compiled {@link Expression}
     * @throws IllegalArgumentException if {@code bundle} is not a recognised singleton
     * @throws WerkflowExpressionEvaluationException if a parse-time limit is exceeded
     */
    public Expression compileWithFunctions(String text, List<FlowableFunctionDelegate> bundle) {
        if (subManagers == null) {
            throw new IllegalStateException(
                    "compileWithFunctions called on a leaf RestrictedExpressionManager");
        }
        RestrictedExpressionManager sub = subManagers.get(bundle);
        if (sub == null) {
            throw new IllegalArgumentException(
                    "Unknown function bundle — must be one of FunctionRegistry.DATE, "
                            + "DATE_STRING, or DATE_STRING_MATH (reference equality required)");
        }
        return sub.createExpression(text);
    }

    /**
     * Compiles {@code text} after enforcing all parse-time hard limits.
     *
     * <p>Checks (in order):
     * <ol>
     *   <li>Length &gt; {@code maxLength} → {@code EL_DENY_LENGTH}</li>
     *   <li>Parenthesis nesting depth &gt; {@code maxDepth} → {@code EL_DENY_DEPTH}</li>
     *   <li>Qualified function-call count &gt; {@code maxFunctionCalls} → {@code EL_DENY_FUNCTIONS}</li>
     * </ol>
     *
     * <p>Per audit doc {@code EL-Expression-Security.md §3.6}: depth and function-call counts
     * use pre-parse heuristics because the JUEL AST ({@code TreeValueExpression.node}) is
     * {@code private transient} with no public accessor in Flowable 7.2 — accessing it would
     * require reflection into Flowable internals.
     *
     * @param text the EL expression text
     * @return the compiled {@link Expression}
     * @throws WerkflowExpressionEvaluationException on limit violation
     */
    @Override
    public Expression createExpression(String text) {
        enforceLength(text);
        enforceDepth(text);
        enforceFunctionCallCount(text);
        return super.createExpression(text);
    }

    // -------------------------------------------------------------------------
    // Private limit checks
    // -------------------------------------------------------------------------

    private void enforceLength(String text) {
        if (text != null && text.length() > limits.getMaxLength()) {
            recordDenialBestEffort(ExpressionErrorCode.EL_DENY_LENGTH, text);
            throw new WerkflowExpressionEvaluationException(
                    ExpressionErrorCode.EL_DENY_LENGTH, "el-parse");
        }
    }

    /**
     * Heuristic depth check: counts maximum open-parenthesis nesting depth.
     *
     * <p>Conservative upper bound — may overcount for string literals containing {@code (},
     * which is acceptable given the 8-level limit is far above any legitimate expression.
     * Cannot undercount actual EL nesting depth.
     */
    private void enforceDepth(String text) {
        if (text == null) {
            return;
        }
        int depth = 0;
        int maxSeen = 0;
        for (int i = 0, len = text.length(); i < len; i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
                if (depth > maxSeen) {
                    maxSeen = depth;
                }
            } else if (c == ')') {
                if (depth > 0) {
                    depth--;
                }
            }
        }
        if (maxSeen > limits.getMaxDepth()) {
            recordDenialBestEffort(ExpressionErrorCode.EL_DENY_DEPTH, text);
            throw new WerkflowExpressionEvaluationException(
                    ExpressionErrorCode.EL_DENY_DEPTH, "el-parse");
        }
    }

    /**
     * Heuristic function-call count: counts each {@code (} immediately preceded by an
     * identifier character — the syntactic shape of every call site ({@code now()},
     * {@code dateUtil.format(...)}, etc.).
     *
     * <p>Conservative upper bound: grouping parentheses such as {@code (a + b)} are
     * preceded by whitespace or an operator and are not counted, while any callable form
     * is. It may overcount if a method-invocation form appears — but those are denied at
     * evaluation time anyway, so an overcount only tightens the parse-time DoS guard. It
     * cannot undercount a real function call.
     */
    private void enforceFunctionCallCount(String text) {
        if (text == null) {
            return;
        }
        int count = 0;
        for (int i = 1, len = text.length(); i < len; i++) {
            if (text.charAt(i) == '(') {
                char before = text.charAt(i - 1);
                if (Character.isLetterOrDigit(before) || before == '_') {
                    count++;
                }
            }
        }
        if (count > limits.getMaxFunctionCalls()) {
            recordDenialBestEffort(ExpressionErrorCode.EL_DENY_FUNCTIONS, text);
            throw new WerkflowExpressionEvaluationException(
                    ExpressionErrorCode.EL_DENY_FUNCTIONS, "el-parse");
        }
    }

    /**
     * Best-effort audit log write — a write failure never suppresses the limit denial.
     */
    private void recordDenialBestEffort(ExpressionErrorCode code, String text) {
        try {
            auditLogger.recordDenial(
                    code,
                    "el-parse",
                    text,
                    null, null, null, null,
                    Map.of());
        } catch (Exception ignored) {
            // intentionally swallowed — audit failure is operational, not a security failure
        }
    }
}
