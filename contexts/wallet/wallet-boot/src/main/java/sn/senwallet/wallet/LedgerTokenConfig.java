package sn.senwallet.wallet;

import java.util.function.Supplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@Configuration(proxyBeanMethods = false)
class LedgerTokenConfig {

    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientService clients) {
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                registrations, clients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder
                .builder()
                .clientCredentials()
                .build());
        return manager;
    }

    @Bean(name = "ledgerAccessToken")
    Supplier<String> ledgerAccessToken(OAuth2AuthorizedClientManager clients) {
        return () -> {
            var authorized = clients.authorize(OAuth2AuthorizeRequest
                    .withClientRegistrationId("wallet-service")
                    .principal("wallet")
                    .build());
            if (authorized == null || authorized.getAccessToken() == null) {
                throw new IllegalStateException(
                        "could not obtain a ledger service token");
            }
            return authorized.getAccessToken().getTokenValue();
        };
    }
}
