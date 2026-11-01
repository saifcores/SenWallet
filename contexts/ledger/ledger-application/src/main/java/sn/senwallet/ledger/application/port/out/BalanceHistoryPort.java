package sn.senwallet.ledger.application.port.out;

import java.time.Instant;
import sn.senwallet.ledger.domain.model.account.LedgerAccount;
import sn.senwallet.ledger.domain.model.account.Balances;

public interface BalanceHistoryPort {

    /**
     * The four counters of an account as they were at {@code asOf}, recomputed
     * from the entries: posted buckets hold transactions posted by then, pending
     * buckets those opened by then and not yet posted or archived.
     */
    Balances balancesAsOf(LedgerAccount account, Instant asOf);
}
