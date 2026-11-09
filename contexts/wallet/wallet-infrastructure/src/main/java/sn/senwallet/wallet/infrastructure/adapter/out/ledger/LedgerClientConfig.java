package sn.senwallet.wallet.infrastructure.adapter.out.ledger;

import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import sn.senwallet.clients.ledger.LedgerClient;

@Configuration(proxyBeanMethods = false)
public class LedgerClientConfig {

    @Bean
    LedgerClient ledgerClient(
            Environment environment,
            @Qualifier("ledgerAccessToken") Supplier<String> accessToken) {
        var baseUrl = environment.getProperty(
                "wallet.ledger.base-url", "http://localhost:8002");
        return new LedgerClient(baseUrl, accessToken);
    }
}
