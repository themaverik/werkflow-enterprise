package com.werkflow.admin.event;

/**
 * Published after a successful datasource update or {@code jdbc-password} credential
 * rotation so that the engine can evict its stale HikariCP pool.
 *
 * <p>Listeners must be annotated with
 * {@link org.springframework.transaction.event.TransactionalEventListener}
 * at {@link org.springframework.transaction.event.TransactionPhase#AFTER_COMMIT}
 * to ensure eviction only happens when the admin DB write has committed.
 * A transaction rollback must not evict a live pool.
 *
 * @param tenantId owning tenant
 * @param ref      datasource reference slug that was updated
 */
public record DatasourcePoolEvictionEvent(String tenantId, String ref) {}
