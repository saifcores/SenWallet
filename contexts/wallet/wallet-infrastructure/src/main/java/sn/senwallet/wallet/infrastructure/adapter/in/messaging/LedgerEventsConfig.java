package sn.senwallet.wallet.infrastructure.adapter.in.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class LedgerEventsConfig {

    static final String EXCHANGE = "ledger";

    @Bean
    TopicExchange ledgerExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue walletLedgerQueue() {
        return new Queue(LedgerHistoryListener.QUEUE, true);
    }

    @Bean
    Binding walletLedgerBinding(Queue walletLedgerQueue, TopicExchange ledgerExchange) {
        return BindingBuilder.bind(walletLedgerQueue).to(ledgerExchange).with("ledger.#");
    }
}
