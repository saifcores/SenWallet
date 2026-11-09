package sn.senwallet.wallet;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sn.senwallet.wallet.application.port.in.ChangeTierUseCase;
import sn.senwallet.wallet.application.port.in.RegisterCustomerUseCase;
import sn.senwallet.wallet.application.port.in.SendTransferUseCase;
import sn.senwallet.wallet.application.port.in.VerifyCustomerUseCase;
import sn.senwallet.wallet.application.port.in.WalletQuery;
import sn.senwallet.wallet.application.port.out.LedgerGateway;
import sn.senwallet.wallet.application.port.out.PricingCatalog;
import sn.senwallet.wallet.application.port.out.SmsPort;
import sn.senwallet.wallet.application.port.out.TransferIdempotency;
import sn.senwallet.wallet.application.port.out.VerifiedCustomerStore;
import sn.senwallet.wallet.application.port.out.WalletHistoryStore;
import sn.senwallet.wallet.application.service.ChangeTierHandler;
import sn.senwallet.wallet.application.service.RecordLedgerEventHandler;
import sn.senwallet.wallet.application.service.RegisterCustomerHandler;
import sn.senwallet.wallet.application.service.SendTransferHandler;
import sn.senwallet.wallet.application.service.VerifyCustomerHandler;
import sn.senwallet.wallet.application.service.WalletQueryService;
import sn.senwallet.wallet.domain.repository.CustomerRepository;
import sn.senwallet.wallet.domain.repository.WalletRepository;

@SpringBootApplication
public class WalletApplication {

    public static void main(String[] args) {
        SpringApplication.run(WalletApplication.class, args);
    }

    @Configuration(proxyBeanMethods = false)
    static class UseCases {

        @Bean
        RegisterCustomerUseCase registerCustomer(CustomerRepository customers) {
            return new RegisterCustomerHandler(customers);
        }

        @Bean
        VerifyCustomerUseCase verifyCustomer(
                CustomerRepository customers,
                WalletRepository wallets,
                VerifiedCustomerStore store,
                LedgerGateway ledger) {
            return new VerifyCustomerHandler(customers, wallets, store, ledger);
        }

        @Bean
        ChangeTierUseCase changeTier(CustomerRepository customers) {
            return new ChangeTierHandler(customers);
        }

        @Bean
        SendTransferUseCase sendTransfer(
                TransferIdempotency idempotency,
                WalletRepository wallets,
                CustomerRepository customers,
                PricingCatalog pricing,
                LedgerGateway ledger) {
            return new SendTransferHandler(idempotency, wallets, customers, pricing, ledger, Clock.systemUTC());
        }

        @Bean
        WalletQuery walletQuery(WalletRepository wallets, LedgerGateway ledger, WalletHistoryStore history) {
            return new WalletQueryService(wallets, ledger, history);
        }

        @Bean
        RecordLedgerEventHandler recordLedgerEvent(
                LedgerGateway ledger,
                WalletRepository wallets,
                WalletHistoryStore history,
                SmsPort sms) {
            return new RecordLedgerEventHandler(ledger, wallets, history, sms);
        }
    }
}
