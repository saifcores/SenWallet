package sn.senwallet.ledger;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sn.senwallet.ledger.application.port.out.BalanceHistoryPort;
import sn.senwallet.ledger.application.port.out.ClockPort;
import sn.senwallet.ledger.application.service.CreateLedgerHandler;
import sn.senwallet.ledger.application.service.LedgerQueryService;
import sn.senwallet.ledger.application.service.OpenAccountHandler;
import sn.senwallet.ledger.application.service.PostTransactionHandler;
import sn.senwallet.ledger.application.service.ReverseHandler;
import sn.senwallet.ledger.application.service.TransitionHandler;
import sn.senwallet.ledger.domain.repository.LedgerAccountRepository;
import sn.senwallet.ledger.domain.repository.LedgerRepository;
import sn.senwallet.ledger.domain.repository.LedgerTransactionRepository;
import sn.senwallet.ledger.infrastructure.adapter.out.clock.SystemClock;

/**
 * The application layer has no Spring stereotypes; its handlers are declared
 * here.
 */
@Configuration(proxyBeanMethods = false)
class UseCaseConfig {

    @Bean
    ClockPort clockPort() {
        return new SystemClock(Clock.systemUTC());
    }

    @Bean
    CreateLedgerHandler createLedgerHandler(LedgerRepository ledgers, ClockPort clock) {
        return new CreateLedgerHandler(ledgers, clock);
    }

    @Bean
    OpenAccountHandler openAccountHandler(LedgerRepository ledgers, LedgerAccountRepository accounts) {
        return new OpenAccountHandler(ledgers, accounts);
    }

    @Bean
    PostTransactionHandler postTransactionHandler(
            LedgerRepository ledgers,
            LedgerTransactionRepository transactions,
            LedgerAccountRepository accounts,
            ClockPort clock) {
        return new PostTransactionHandler(ledgers, transactions, accounts, clock);
    }

    @Bean
    TransitionHandler transitionHandler(
            LedgerTransactionRepository transactions, LedgerAccountRepository accounts, ClockPort clock) {
        return new TransitionHandler(transactions, accounts, clock);
    }

    @Bean
    ReverseHandler reverseHandler(
            LedgerTransactionRepository transactions, LedgerAccountRepository accounts, ClockPort clock) {
        return new ReverseHandler(transactions, accounts, clock);
    }

    @Bean
    LedgerQueryService ledgerQueryService(
            LedgerRepository ledgers,
            LedgerAccountRepository accounts,
            LedgerTransactionRepository transactions,
            BalanceHistoryPort history) {
        return new LedgerQueryService(ledgers, accounts, transactions, history);
    }
}
