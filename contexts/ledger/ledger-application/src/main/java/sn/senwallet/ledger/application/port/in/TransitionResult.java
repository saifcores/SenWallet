package sn.senwallet.ledger.application.port.in;

import java.util.List;
import sn.senwallet.ledger.domain.model.transaction.LedgerTransaction;

/**
 * {@code Transitioned}: the status changed in this call. {@code Unchanged}: the
 * transaction was already in the requested status (a retried call).
 */
public sealed interface TransitionResult {

    LedgerTransaction transaction();

    List<AccountBalances> balances();

    record Transitioned(LedgerTransaction transaction, List<AccountBalances> balances) implements TransitionResult {
    }

    record Unchanged(LedgerTransaction transaction, List<AccountBalances> balances) implements TransitionResult {
    }
}
