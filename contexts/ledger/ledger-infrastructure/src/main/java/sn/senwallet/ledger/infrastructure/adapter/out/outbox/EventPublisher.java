package sn.senwallet.ledger.infrastructure.adapter.out.outbox;

import java.util.List;

/**
 * Publishes a batch. A failure must leave the caller's transaction uncommitted.
 */
public interface EventPublisher {

    void publish(List<OutboxMessage> messages);
}
