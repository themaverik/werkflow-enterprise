package com.werkflow.admin.service;

import com.werkflow.admin.event.DatasourcePoolEvictionEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for {@link DatasourcePoolEvictionEvent} and delegates to
 * {@link DatasourceEvictClient} after the publishing transaction commits.
 *
 * <p>The {@code AFTER_COMMIT} phase guarantees that a transaction rollback
 * does not trigger an eviction of a live pool — eviction only happens once
 * the admin DB write is durable.
 */
@Component
@RequiredArgsConstructor
public class DatasourcePoolEvictionListener {

    private final DatasourceEvictClient evictClient;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEviction(DatasourcePoolEvictionEvent event) {
        evictClient.evict(event.tenantId(), event.ref());
    }
}
