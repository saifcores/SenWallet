package sn.senwallet.payments.infrastructure.adapter.in.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sn.senwallet.events.WalletEventTypes;

@Configuration(proxyBeanMethods = false)
public class WalletBindingConfig {

    @Bean
    TopicExchange walletExchange() {
        return new TopicExchange(WalletEventTypes.AGGREGATE_TYPE, true, false);
    }

    @Bean
    Queue paymentsWalletQueue() {
        return new Queue(WalletProjectionListener.QUEUE, true);
    }

    @Bean
    Binding paymentsWalletBinding(Queue paymentsWalletQueue, TopicExchange walletExchange) {
        return BindingBuilder.bind(paymentsWalletQueue).to(walletExchange).with("wallet.#");
    }
}
