package sn.senwallet.ledger.application.port.in;

import sn.senwallet.ledger.domain.model.account.LedgerAccount;
import sn.senwallet.ledger.domain.model.ledger.Ledger;
import sn.senwallet.ledger.domain.model.transaction.LedgerTransaction;
import sn.senwallet.shared.id.AccountId;
import sn.senwallet.shared.id.LedgerId;
import sn.senwallet.shared.id.TransactionId;

/** Reads by id; each method throws the matching not-found exception. */
public interface LedgerLookupQuery {

    Ledger ledger(LedgerId id);

    LedgerAccount account(AccountId id);

    LedgerTransaction transaction(TransactionId id);
}
