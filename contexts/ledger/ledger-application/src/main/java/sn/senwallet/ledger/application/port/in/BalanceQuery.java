package sn.senwallet.ledger.application.port.in;

import java.time.Instant;
import sn.senwallet.shared.id.AccountId;

public interface BalanceQuery {

    /** Current balances, read from the counters. */
    AccountBalances current(AccountId accountId);

    /**
     * Balances at a past instant, recomputed from the entries; the lock version
     * is reported as 0.
     */
    AccountBalances asOf(AccountId accountId, Instant asOf);
}
