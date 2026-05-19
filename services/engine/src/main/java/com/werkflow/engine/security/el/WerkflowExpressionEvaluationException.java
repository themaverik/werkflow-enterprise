package com.werkflow.engine.security.el;

/**
 * Sanitised exception surfaced when EL evaluation fails (denial or runtime error).
 *
 * <p>Per audit doc {@code EL-Expression-Security.md} §3.5 and decision D-EL-7,
 * this exception's message contains only a stable {@link ExpressionErrorCode}
 * and a site identifier (e.g., {@code "action-block:set-vars-1"}). The original
 * expression text, variable names, and class paths are not exposed in the
 * user-facing message — they are written to the server-side audit log
 * by {@link ExpressionAuditLogger} for {@code ROLE_PLATFORM_ADMIN} forensic use.
 *
 * <p>Unchecked so it can propagate through Flowable's evaluation boundary
 * without forcing every delegate to declare it.
 */
public class WerkflowExpressionEvaluationException extends RuntimeException {

    private final ExpressionErrorCode code;
    private final String siteId;

    public WerkflowExpressionEvaluationException(ExpressionErrorCode code, String siteId) {
        super(buildMessage(code, siteId));
        this.code = code;
        this.siteId = siteId;
    }

    public WerkflowExpressionEvaluationException(ExpressionErrorCode code, String siteId, Throwable cause) {
        super(buildMessage(code, siteId), cause);
        this.code = code;
        this.siteId = siteId;
    }

    public ExpressionErrorCode getCode() {
        return code;
    }

    public String getSiteId() {
        return siteId;
    }

    private static String buildMessage(ExpressionErrorCode code, String siteId) {
        return code.name() + " at " + (siteId == null ? "unknown-site" : siteId);
    }
}
