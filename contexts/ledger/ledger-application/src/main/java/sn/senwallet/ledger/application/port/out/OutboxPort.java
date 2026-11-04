package sn.senwallet.ledger.application.port.out;

import java.util.List;
import sn.senwallet.ledger.domain.event.LedgerTransactionEvent;
import sn.senwallet.ledger.domain.model.transaction.LedgerTransaction;

/**
 * Appends facts in the caller's database transaction, so a rollback leaves
 * no event behind.
 */
public interface OutboxPort {

    void append(LedgerTransaction transaction, List<LedgerTransactionEvent> events);
}
