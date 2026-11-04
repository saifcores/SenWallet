package sn.senwallet.ledger.application.service;

import org.springframework.transaction.annotation.Transactional;
import sn.senwallet.ledger.application.command.OpenAccountCommand;
import sn.senwallet.ledger.application.exception.LedgerNotFoundException;
import sn.senwallet.ledger.application.port.in.OpenAccountUseCase;
import sn.senwallet.ledger.domain.model.account.LedgerAccount;
import sn.senwallet.ledger.domain.repository.LedgerAccountRepository;
import sn.senwallet.ledger.domain.repository.LedgerRepository;
import sn.senwallet.shared.id.AccountId;

@Transactional
public class OpenAccountHandler implements OpenAccountUseCase {

    private final LedgerRepository ledgers;
    private final LedgerAccountRepository accounts;

    public OpenAccountHandler(LedgerRepository ledgers, LedgerAccountRepository accounts) {
        this.ledgers = ledgers;
        this.accounts = accounts;
    }

    @Override
    public LedgerAccount open(OpenAccountCommand cmd) {
        ledgers.findById(cmd.ledgerId()).orElseThrow(() -> new LedgerNotFoundException(cmd.ledgerId()));
        var account = LedgerAccount.open(AccountId.newId(), cmd.ledgerId(), cmd.name(), cmd.normalBalance(),
                cmd.currency(), cmd.balanceMode(), cmd.metadata());
        accounts.insert(account);
        return account;
    }
}
