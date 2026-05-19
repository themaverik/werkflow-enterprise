package com.werkflow.engine.security.el;

import com.werkflow.engine.audit.ProcessAuditLogRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.common.engine.impl.variable.MapDelegateVariableContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Pure-unit (Mockito-only) verification for P1-9 and P1-11 EL-Expression-Security
 * audit items. No Spring context or database required.
 *
 * <p><b>P1-9 — Reflection denial:</b> Constructs {@link RestrictedExpressionManager}
 * standalone and verifies that method invocations on any EL base object are blocked
 * by {@link SecurityELResolver#invoke}. Flowable wraps the denial in a
 * {@link org.flowable.common.engine.api.FlowableException} — the test unwraps the
 * cause chain to assert the {@link WerkflowExpressionEvaluationException} code.
 *
 * <p><b>P1-11 — Bundle scoping (structural contract):</b> Verifies that
 * {@link RestrictedExpressionManager#compileWithFunctions} routes through the correct
 * pre-built sub-manager and enforces the bundle registry contract. Full function
 * evaluation (which proves JUEL resolution of {@code dateUtil.now()}) requires the
 * Spring-managed process engine context and is covered by
 * {@link ELSecurityVerificationSuiteTest} (P1-11 integration variant).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EL-Expression-Security Unit Verification (P1-9, P1-11 structural)")
class ELSecurityUnitVerificationTest {

    @Mock
    private ProcessAuditLogRepository auditLogRepository;

    @Mock
    private MeterRegistry meterRegistry;

    private RestrictedExpressionManager manager;

    @BeforeEach
    void setUp() {
        ExpressionLimitsConfig limits = new ExpressionLimitsConfig();
        ExpressionAuditLogger auditLogger = new ExpressionAuditLogger(auditLogRepository, meterRegistry);
        // Empty beans map — sufficient for reflection-denial and structural bundle tests
        // that do not involve Spring bean resolution or JUEL function execution.
        manager = new RestrictedExpressionManager(limits, auditLogger, Map.of());
    }

    /** Shared empty variable scope. */
    private static MapDelegateVariableContainer emptyVars() {
        return new MapDelegateVariableContainer();
    }

    // =========================================================================
    // P1-9: Reflection denied — method invocation on any EL base object
    // =========================================================================

    @Nested
    @DisplayName("P1-9 — reflection denied at EL evaluation (standalone manager)")
    class P1_9_ReflectionDenied {

        /**
         * {@code ${''.getClass()}} resolves the string literal to a Java String, then invokes
         * {@code getClass()} on it. {@link SecurityELResolver#invoke} fires and throws
         * {@link WerkflowExpressionEvaluationException}({@link ExpressionErrorCode#EL_DENY_REFLECTION}).
         *
         * <p>Flowable's {@code JuelExpression.getValue} wraps the denial in a
         * {@link org.flowable.common.engine.api.FlowableException}. The test walks the
         * cause chain to find and assert the {@link WerkflowExpressionEvaluationException}.
         */
        @Test
        @DisplayName("getClass() on string literal: WerkflowExpressionEvaluationException(EL_DENY_REFLECTION) in cause chain")
        void getClass_onStringLiteral_reflectionDenialInCauseChain() {
            // Arrange
            Expression expression = manager.createExpression("${''.getClass()}");

            // Act
            Throwable thrown = catchThrowable(() -> expression.getValue(emptyVars()));

            // Assert — WerkflowExpressionEvaluationException is in the cause chain
            assertThat(thrown).isNotNull();
            WerkflowExpressionEvaluationException denial = findDenialException(thrown);
            assertThat(denial)
                    .as("WerkflowExpressionEvaluationException must appear in cause chain")
                    .isNotNull();
            assertThat(denial.getCode()).isEqualTo(ExpressionErrorCode.EL_DENY_REFLECTION);
        }

        /**
         * Chained form: {@code ${''.getClass().forName('java.io.File')}}.
         * The denial fires on {@code getClass()} before {@code forName()} is reached.
         */
        @Test
        @DisplayName("getClass().forName() chain: WerkflowExpressionEvaluationException(EL_DENY_REFLECTION) in cause chain")
        void getClassForName_chain_reflectionDenialInCauseChain() {
            // Arrange
            Expression expression =
                    manager.createExpression("${''.getClass().forName('java.io.File')}");

            // Act
            Throwable thrown = catchThrowable(() -> expression.getValue(emptyVars()));

            // Assert
            assertThat(thrown).isNotNull();
            WerkflowExpressionEvaluationException denial = findDenialException(thrown);
            assertThat(denial)
                    .as("WerkflowExpressionEvaluationException must appear in cause chain")
                    .isNotNull();
            assertThat(denial.getCode()).isEqualTo(ExpressionErrorCode.EL_DENY_REFLECTION);
        }

        /** Walks the cause chain for {@link WerkflowExpressionEvaluationException}. */
        private WerkflowExpressionEvaluationException findDenialException(Throwable thrown) {
            Throwable current = thrown;
            while (current != null) {
                if (current instanceof WerkflowExpressionEvaluationException wex) {
                    return wex;
                }
                current = current.getCause();
            }
            return null;
        }
    }

    // =========================================================================
    // P1-11: compileWithFunctions — structural contract (bundle routing)
    // =========================================================================

    @Nested
    @DisplayName("P1-11 — compileWithFunctions structural contract (bundle routing, scope guard)")
    class P1_11_BundleStructuralContract {

        /**
         * {@code compileWithFunctions(text, DATE)} must return a non-null Expression
         * without parse-limit violations — the DATE bundle is a valid registered singleton.
         */
        @Test
        @DisplayName("compileWithFunctions with DATE bundle returns a non-null Expression")
        void compileWithFunctions_dateBundleValidExpression_returnsExpression() {
            // Act
            Expression expression = manager.compileWithFunctions("${dateUtil.now()}", FunctionRegistry.DATE);

            // Assert
            assertThat(expression).isNotNull();
            assertThat(expression.getExpressionText()).isEqualTo("${dateUtil.now()}");
        }

        /**
         * {@code compileWithFunctions(text, DATE_STRING)} routes to the DATE_STRING sub-manager.
         * The compile must succeed for a valid expression.
         */
        @Test
        @DisplayName("compileWithFunctions with DATE_STRING bundle returns a non-null Expression")
        void compileWithFunctions_dateStringBundle_returnsExpression() {
            // Act
            Expression expression = manager.compileWithFunctions(
                    "${stringUtil.upper('hello')}", FunctionRegistry.DATE_STRING);

            // Assert
            assertThat(expression).isNotNull();
        }

        /**
         * Passing an unknown (arbitrary) list as the bundle must throw
         * {@link IllegalArgumentException} — only the three singleton list constants from
         * {@link FunctionRegistry} are valid (identity equality required).
         *
         * <p>Note: {@code List.copyOf(FunctionRegistry.DATE)} may return the SAME instance
         * (the JDK optimisation for already-unmodifiable lists), so we construct a genuine
         * fresh {@link java.util.ArrayList} to guarantee a distinct reference.
         */
        @Test
        @DisplayName("compileWithFunctions with unknown bundle list throws IllegalArgumentException")
        void compileWithFunctions_unknownBundle_throwsIllegalArgument() {
            // Arrange — a genuinely new list instance (not the FunctionRegistry singleton)
            List<org.flowable.common.engine.api.delegate.FlowableFunctionDelegate> unknownBundle =
                    new java.util.ArrayList<>(FunctionRegistry.DATE);

            // Act + Assert
            assertThatThrownBy(() -> manager.compileWithFunctions("${dateUtil.now()}", unknownBundle))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown function bundle");
        }

        /**
         * Confirms the three canonical bundle singletons are distinct by identity —
         * this is the invariant that makes the {@code IdentityHashMap} sub-manager lookup safe.
         */
        @Test
        @DisplayName("FunctionRegistry bundle singletons are distinct by reference identity")
        void functionRegistry_bundleSingletons_areDistinctByIdentity() {
            assertThat(FunctionRegistry.DATE)
                    .isNotSameAs(FunctionRegistry.DATE_STRING)
                    .isNotSameAs(FunctionRegistry.DATE_STRING_MATH);
            assertThat(FunctionRegistry.DATE_STRING)
                    .isNotSameAs(FunctionRegistry.DATE_STRING_MATH);
        }

        /**
         * DATE bundle must not contain any stringUtil delegates.
         * This is the structural proof that the scoping is enforced at registration time.
         */
        @Test
        @DisplayName("DATE bundle contains only dateUtil delegates (no stringUtil or mathUtil)")
        void dateBundleContents_onlyDateUtilDelegates() {
            long nonDateCount = FunctionRegistry.DATE.stream()
                    .filter(d -> d instanceof SafeFunctionDelegate sfd
                            && !"dateUtil".equals(sfd.prefix()))
                    .count();

            assertThat(nonDateCount)
                    .as("DATE bundle must contain only dateUtil delegates")
                    .isZero();
        }

        /**
         * DATE_STRING bundle must contain both dateUtil and stringUtil delegates.
         */
        @Test
        @DisplayName("DATE_STRING bundle contains dateUtil and stringUtil delegates")
        void dateStringBundleContents_dateAndStringDelegates() {
            boolean hasDate = FunctionRegistry.DATE_STRING.stream()
                    .anyMatch(d -> d instanceof SafeFunctionDelegate sfd
                            && "dateUtil".equals(sfd.prefix()));
            boolean hasString = FunctionRegistry.DATE_STRING.stream()
                    .anyMatch(d -> d instanceof SafeFunctionDelegate sfd
                            && "stringUtil".equals(sfd.prefix()));

            assertThat(hasDate).as("DATE_STRING must have dateUtil").isTrue();
            assertThat(hasString).as("DATE_STRING must have stringUtil").isTrue();
        }
    }
}
