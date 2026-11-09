package sn.senwallet.wallet.infrastructure.adapter.out.ledger;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import sn.senwallet.clients.ledger.LedgerClient;

@Configuration(proxyBeanMethods = false)
public class LedgerClientConfig {

    @Bean
    LedgerClient ledgerClient(Environment environment) {
        return new LedgerClient(environment.getProperty("wallet.ledger.base-url", "http://localhost:8002"));
    }
}
