package sn.senwallet.ledger;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import sn.senwallet.ledger.application.command.CreateLedgerCommand;
import sn.senwallet.ledger.application.command.OpenAccountCommand;
import sn.senwallet.ledger.application.port.in.CreateLedgerUseCase;
import sn.senwallet.ledger.application.port.in.OpenAccountUseCase;
import sn.senwallet.ledger.domain.model.account.BalanceMode;
import sn.senwallet.ledger.domain.model.account.NormalBalance;
import sn.senwallet.ledger.domain.model.ledger.LedgerEnvironment;
import sn.senwallet.shared.id.AccountId;
import sn.senwallet.shared.id.LedgerId;
import sn.senwallet.shared.money.Currency;
import sn.senwallet.testsupport.SenwalletKeycloak;
import sn.senwallet.testsupport.SenwalletPostgres;

/**
 * Boots the ledger service on a random port against the shared PostgreSQL
 * container, connected as {@code ledger_app}. Every test creates its own
 * ledger, so tests never see each other's accounts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class LedgerIntegrationTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        SenwalletPostgres.ledgerProperties().forEach((key, value) -> registry.add(key, () -> value));
        SenwalletKeycloak.ledgerProperties().forEach((key, value) -> registry.add(key, () -> value));
        registry.add("ledger.outbox.relay-enabled", () -> "false");
    }

    @LocalServerPort
    int port;

    @Autowired
    CreateLedgerUseCase createLedger;

    @Autowired
    OpenAccountUseCase openAccount;

    Api api() {
        return new Api(port);
    }

    LedgerId newLedger() {
        return createLedger.create(new CreateLedgerCommand("it-" + getClass().getSimpleName(),
                LedgerEnvironment.TEST)).id();
    }

    AccountId openAccount(LedgerId ledger, String name, NormalBalance normal) {
        return openAccount.open(new OpenAccountCommand(ledger, name, normal, Currency.XOF, BalanceMode.SYNC,
                Map.of())).id();
    }
}
