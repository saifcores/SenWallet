package sn.senwallet.events;

import java.util.Set;

/** Routing keys and aggregate name for ledger transaction facts. */
public final class LedgerEventTypes {

    public static final String AGGREGATE_TYPE = "ledger_transaction";
    public static final String TRANSACTION_PENDING = "ledger.transaction.pending";
    public static final String TRANSACTION_POSTED = "ledger.transaction.posted";
    public static final String TRANSACTION_ARCHIVED = "ledger.transaction.archived";

    public static final Set<String> ALL = Set.of(
            TRANSACTION_PENDING, TRANSACTION_POSTED, TRANSACTION_ARCHIVED);

    private LedgerEventTypes() {
    }
}
