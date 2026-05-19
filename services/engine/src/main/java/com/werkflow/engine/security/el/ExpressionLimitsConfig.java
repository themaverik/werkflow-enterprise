package com.werkflow.engine.security.el;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration for the parse-time hard limits enforced by
 * {@link RestrictedExpressionManager}. Per audit doc {@code EL-Expression-Security.md}
 * decision D-EL-8: defaults 4096 / 8 / 16 with per-deployment overrides via
 * {@code application.yml}.
 *
 * <p>Example {@code application.yml} fragment:
 * <pre>
 * werkflow:
 *   el:
 *     max-length: 4096
 *     max-depth: 8
 *     max-function-calls: 16
 * </pre>
 *
 * <p>Defaults are conservative — 50× headroom over the longest current
 * Werkflow sample expression (~85 chars), 2× over deepest nesting (4 levels),
 * 8× over the most function-call-heavy expression (2 calls).
 */
@ConfigurationProperties(prefix = "werkflow.el")
public class ExpressionLimitsConfig {

    private int maxLength = 4096;
    private int maxDepth = 8;
    private int maxFunctionCalls = 16;

    public int getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public int getMaxFunctionCalls() {
        return maxFunctionCalls;
    }

    public void setMaxFunctionCalls(int maxFunctionCalls) {
        this.maxFunctionCalls = maxFunctionCalls;
    }
}
