package sn.senwallet.ledger.infrastructure.adapter.out.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** One batch per tick. Off in tests that do not run a broker. */
@Component
@ConditionalOnProperty(name = "ledger.outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayScheduler {

    private final OutboxRelay relay;

    public OutboxRelayScheduler(OutboxRelay relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${ledger.outbox.delay:1000}")
    public void relay() {
        relay.publishBatch();
    }
}
