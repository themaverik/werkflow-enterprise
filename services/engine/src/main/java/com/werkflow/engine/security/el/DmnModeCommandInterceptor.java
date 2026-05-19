package com.werkflow.engine.security.el;

import org.flowable.common.engine.impl.interceptor.AbstractCommandInterceptor;
import org.flowable.common.engine.impl.interceptor.Command;
import org.flowable.common.engine.impl.interceptor.CommandConfig;
import org.flowable.common.engine.impl.interceptor.CommandExecutor;

/**
 * DMN command interceptor that gates {@link SecurityELResolver} DMN pass-through mode.
 *
 * <p>Installed as a custom pre-command interceptor on the DMN engine (audit doc
 * {@code EL-Expression-Security.md §4.5}, item P1-7). For the entire duration of a DMN
 * command — including FEEL expression evaluation inside {@code ExecuteDecisionCmd} /
 * {@code ExecuteDecisionWithAuditTrailCmd} — the {@link SecurityELResolver} ThreadLocal
 * flag is set to {@code true}. This allows the FEEL evaluator to process its expressions
 * unmodified rather than being blocked by the BPMN-oriented EL guard.
 *
 * <p>Thread safety: uses a {@link ThreadLocal} flag; Flowable's thread-pool model means
 * each command executes on a single thread from entry to exit. {@link SecurityELResolver#exitDmnMode()}
 * calls {@code ThreadLocal.remove()} (not {@code set(false)}), ensuring no stale flag
 * survives thread-pool reuse even if an unexpected exception bypasses the try-block.
 *
 * <p>Nesting: a DMN decision may invoke a sub-decision on the same thread, re-entering
 * this interceptor. The flag is captured on entry and cleared only by the outermost
 * invocation — an inner {@code finally} never clears a flag the outer scope still needs.
 *
 * <p>Registered via {@link com.werkflow.engine.config.DmnSecurityConfig}.
 */
public class DmnModeCommandInterceptor extends AbstractCommandInterceptor {

    /**
     * Wraps DMN command execution with DMN mode activation.
     *
     * <p>Pattern: capture prior state → enter → try { delegate } finally { exit if outermost }.
     * The {@code finally} block guarantees DMN mode is cleared on every exit path — including
     * exceptions from the command, the FEEL evaluator, or Flowable internals — but only when
     * this is the outermost DMN command on the thread, so a nested sub-decision evaluation
     * does not strand the outer scope in non-DMN mode.
     *
     * @param config          command configuration
     * @param command         the DMN command to execute (e.g. ExecuteDecisionCmd)
     * @param commandExecutor the executor to delegate to
     * @param <T>             the command result type
     * @return the command result
     */
    @Override
    public <T> T execute(CommandConfig config, Command<T> command, CommandExecutor commandExecutor) {
        boolean alreadyInDmnMode = SecurityELResolver.isInDmnMode();
        SecurityELResolver.enterDmnMode();
        try {
            return getNext().execute(config, command, commandExecutor);
        } finally {
            if (!alreadyInDmnMode) {
                SecurityELResolver.exitDmnMode();
            }
        }
    }
}
