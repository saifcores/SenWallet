package sn.senwallet.ledger.application.port.in;

import sn.senwallet.ledger.application.command.CreateLedgerCommand;
import sn.senwallet.ledger.domain.model.ledger.Ledger;

public interface CreateLedgerUseCase {

    Ledger create(CreateLedgerCommand command);
}
