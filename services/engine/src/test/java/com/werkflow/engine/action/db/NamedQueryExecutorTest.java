package com.werkflow.engine.action.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link NamedQueryExecutor} covering DML rejection and row-limit capping.
 * These tests verify the safety invariants without a live database.
 */
class NamedQueryExecutorTest {

    // -------------------------------------------------------------------------
    // DML rejection
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "rejectDml rejects keyword: {0}")
    @ValueSource(strings = {
        "INSERT INTO orders VALUES (1, 'x')",
        "UPDATE orders SET status = 'done'",
        "DELETE FROM orders WHERE id = 1",
        "MERGE INTO orders USING src ON ...",
        "TRUNCATE TABLE orders",
        "DROP TABLE orders",
        "ALTER TABLE orders ADD COLUMN foo TEXT",
        "CREATE TABLE foo (id BIGINT)",
        "GRANT SELECT ON orders TO app",
        "REVOKE ALL ON orders FROM guest"
    })
    void rejectDml_throwsForDmlKeywords(String sql) {
        assertThatThrownBy(() -> NamedQueryExecutor.rejectDml(sql))
            .isInstanceOf(IllegalArgumentException.class)
            .satisfies(e -> assertThat(e.getMessage()).containsIgnoringCase("readOnly"));
    }

    @Test
    void rejectDml_allowsSelectStatement() {
        assertThatNoException().isThrownBy(() ->
            NamedQueryExecutor.rejectDml("SELECT id, name FROM employees WHERE dept = :dept"));
    }

    @Test
    void rejectDml_isCaseInsensitive() {
        assertThatThrownBy(() -> NamedQueryExecutor.rejectDml("insert into foo values (1)"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NamedQueryExecutor.rejectDml("iNsErT into foo values (1)"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectDml_allowsNullAndBlank() {
        assertThatNoException().isThrownBy(() -> NamedQueryExecutor.rejectDml(null));
        assertThatNoException().isThrownBy(() -> NamedQueryExecutor.rejectDml(""));
        assertThatNoException().isThrownBy(() -> NamedQueryExecutor.rejectDml("   "));
    }

    @Test
    void rejectDml_errorMessageIncludesKeyword() {
        assertThatThrownBy(() -> NamedQueryExecutor.rejectDml("DELETE FROM orders"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("DELETE");
    }

    @Test
    void rejectDml_allowsSelectWithInsertInString() {
        // Column value containing "INSERT" should not trip the guard at word boundary
        assertThatNoException().isThrownBy(() ->
            NamedQueryExecutor.rejectDml("SELECT * FROM audit WHERE action = 'INSERT_RECORD'"));
    }

    // -------------------------------------------------------------------------
    // Max-rows capping (via the package-internal constant)
    // -------------------------------------------------------------------------

    @Test
    void hardCap_isDefinedAndReasonable() {
        assertThat(NamedQueryExecutor.HARD_CAP_MAX_ROWS).isEqualTo(10_000);
        assertThat(NamedQueryExecutor.DEFAULT_MAX_ROWS).isEqualTo(1_000);
        assertThat(NamedQueryExecutor.DEFAULT_TIMEOUT_SECONDS).isEqualTo(30);
    }
}
