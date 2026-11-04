package sn.senwallet.ledger.application.port.in;

import sn.senwallet.ledger.application.command.OpenAccountCommand;
import sn.senwallet.ledger.domain.model.account.LedgerAccount;

public interface OpenAccountUseCase {

    LedgerAccount open(OpenAccountCommand command);
}
