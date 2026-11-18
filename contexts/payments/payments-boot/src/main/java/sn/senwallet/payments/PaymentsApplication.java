package sn.senwallet.payments;

import java.time.Clock;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import sn.senwallet.clients.ledger.LedgerClient;
import sn.senwallet.payments.application.port.in.StartPaymentUseCase;
import sn.senwallet.payments.application.port.out.IdempotencyStore;
import sn.senwallet.payments.application.port.out.InquirySchedule;
import sn.senwallet.payments.application.port.out.LedgerPort;
import sn.senwallet.payments.application.port.out.PaymentOrderRepository;
import sn.senwallet.payments.application.port.out.PricingCatalog;
import sn.senwallet.payments.application.port.out.RailPort;
import sn.senwallet.payments.application.port.out.WalletDirectory;
import sn.senwallet.payments.application.port.out.CallbackInbox;
import sn.senwallet.payments.application.service.CallbackProcessor;
import sn.senwallet.payments.application.service.InboundCredit;
import sn.senwallet.payments.application.service.InquiryProcessor;
import sn.senwallet.payments.application.service.OrderProgress;
import sn.senwallet.payments.application.service.StartPaymentHandler;

@SpringBootApplication
@EnableScheduling
public class PaymentsApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentsApplication.class, args);
    }

    @Configuration(proxyBeanMethods = false)
    static class UseCases {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        OrderProgress orderProgress(
                PaymentOrderRepository orders,
                LedgerPort ledger,
                InquirySchedule schedule,
                Clock clock) {
            return new OrderProgress(orders, ledger, schedule, clock);
        }

        @Bean
        InboundCredit inboundCredit(
                PaymentOrderRepository orders,
                WalletDirectory wallets,
                LedgerPort ledger,
                Clock clock) {
            return new InboundCredit(orders, wallets, ledger, clock);
        }

        @Bean
        CallbackProcessor callbackProcessor(
                CallbackInbox inbox,
                PaymentOrderRepository orders,
                OrderProgress progress,
                InboundCredit inbound) {
            return new CallbackProcessor(inbox, orders, progress, inbound);
        }

        @Bean
        InquiryProcessor inquiryProcessor(
                PaymentOrderRepository orders,
                RailPort rails,
                OrderProgress progress) {
            return new InquiryProcessor(orders, rails, progress);
        }

        @Bean
        StartPaymentUseCase startPayment(
                IdempotencyStore idempotency,
                PaymentOrderRepository orders,
                WalletDirectory wallets,
                PricingCatalog pricing,
                LedgerPort ledger,
                RailPort rails,
                OrderProgress progress,
                Clock clock) {
            return new StartPaymentHandler(idempotency, orders, wallets, pricing, ledger, rails, progress, clock);
        }

        @Bean
        LedgerClient ledgerClient(
                Environment environment,
                @Qualifier("ledgerAccessToken") Supplier<String> accessToken) {
            var baseUrl = environment.getProperty("payments.ledger.base-url", "http://localhost:8002");
            return new LedgerClient(baseUrl, accessToken);
        }

        @Bean
        OAuth2AuthorizedClientManager authorizedClientManager(
                ClientRegistrationRepository registrations,
                OAuth2AuthorizedClientService clients) {
            var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients);
            manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                    .clientCredentials()
                    .build());
            return manager;
        }

        @Bean(name = "ledgerAccessToken")
        Supplier<String> ledgerAccessToken(OAuth2AuthorizedClientManager clients) {
            return () -> {
                var authorized = clients.authorize(OAuth2AuthorizeRequest
                        .withClientRegistrationId("payments-service")
                        .principal("payments")
                        .build());
                if (authorized == null || authorized.getAccessToken() == null) {
                    throw new IllegalStateException("could not obtain a ledger service token");
                }
                return authorized.getAccessToken().getTokenValue();
            };
        }
    }
}
