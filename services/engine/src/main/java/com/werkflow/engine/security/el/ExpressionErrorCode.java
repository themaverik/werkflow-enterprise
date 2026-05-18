package com.werkflow.engine.security.el;

/**
 * Stable error codes returned in {@link WerkflowExpressionEvaluationException} messages.
 *
 * <p>User-facing messages contain only the code and a site identifier — never the
 * offending expression text, variable names, or class paths. The full unsanitised
 * expression body is recorded in the server-side audit log
 * ({@link ExpressionAuditLogger}) for forensic use under
 * {@code ROLE_PLATFORM_ADMIN}.
 *
 * <p>Codes are stable strings (not ordinal-indexed) so downstream alerting and
 * runbooks can match against them without re-ordering hazard.
 */
public enum ExpressionErrorCode {

    /** Method invocation blocked by {@link SecurityELResolver} — POJO method or reflection access. */
    EL_DENY_REFLECTION,

    /** Expression text exceeds {@code werkflow.el.maxLength}. */
    EL_DENY_LENGTH,

    /** Parsed AST exceeds {@code werkflow.el.maxDepth}. */
    EL_DENY_DEPTH,

    /** Parsed AST exceeds {@code werkflow.el.maxFunctionCalls}. */
    EL_DENY_FUNCTIONS,

    /** Variable referenced is not in the execution variable container. */
    EL_UNKNOWN_VARIABLE,

    /** Function referenced is not registered. */
    EL_UNKNOWN_FUNCTION,

    /** Generic parse error (syntax). */
    EL_PARSE_ERROR,

    /** Generic runtime error (evaluation failed for a reason not covered by the deny codes). */
    EL_RUNTIME_ERROR
}
