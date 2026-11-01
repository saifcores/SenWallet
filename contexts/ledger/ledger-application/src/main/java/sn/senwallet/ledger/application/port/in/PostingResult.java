package sn.senwallet.ledger.application.port.in;

import java.util.List;
import sn.senwallet.ledger.domain.model.transaction.LedgerTransaction;

/**
 * {@code Created}: the transaction was written by this call. {@code Replayed}:
 * the same request had already been posted; nothing was written.
 */
public sealed interface PostingResult {

    LedgerTransaction transaction();

    /** Balances of the accounts touched by the transaction, after it. */
    List<AccountBalances> balances();

    record Created(LedgerTransaction transaction, List<AccountBalances> balances) implements PostingResult {
    }

    record Replayed(LedgerTransaction transaction, List<AccountBalances> balances) implements PostingResult {
    }
}
