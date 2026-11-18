package sn.senwallet.wallet.infrastructure.adapter.out.outbox;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

@Component
@ConditionalOnProperty(name = "wallet.outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
public class WalletOutboxRelayScheduler {

    private final WalletOutboxRelay relay;

    public WalletOutboxRelayScheduler(
            JdbcClient jdbc,
            RabbitTemplate rabbit,
            AmqpAdmin admin,
            PlatformTransactionManager transactions,
            @Value("${wallet.outbox.batch-size:100}") int batchSize) {
        this.relay = new WalletOutboxRelay(jdbc, rabbit, admin, transactions, batchSize);
    }

    @Scheduled(fixedDelayString = "${wallet.outbox.delay:1000}")
    void relay() {
        relay.publishBatch();
    }
}
