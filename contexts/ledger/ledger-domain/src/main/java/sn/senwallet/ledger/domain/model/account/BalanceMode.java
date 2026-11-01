package sn.senwallet.ledger.domain.model.account;

/**
 * {@code SYNC} accounts are locked and updated in the posting transaction;
 * {@code ASYNC} accounts (shared hot accounts such as revenue) take no lock and
 * therefore cannot carry a balance condition (ADR-05).
 */
public enum BalanceMode {
    SYNC,
    ASYNC
}
