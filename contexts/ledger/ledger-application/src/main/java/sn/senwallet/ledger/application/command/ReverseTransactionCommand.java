package sn.senwallet.ledger.application.command;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import sn.senwallet.ledger.domain.model.condition.BalanceCondition;
import sn.senwallet.ledger.domain.model.transaction.ExternalId;
import sn.senwallet.shared.id.TransactionId;

/**
 * Reverses a posted transaction. Without an external id, the reversal uses
 * {@code reversal:<transaction id>}, so a retried call is a replay.
 */
public record ReverseTransactionCommand(
        TransactionId transactionId, Optional<ExternalId> externalId, List<BalanceCondition> conditions) {

    public ReverseTransactionCommand {
        Objects.requireNonNull(transactionId, "transactionId");
        externalId = Objects.requireNonNullElse(externalId, Optional.empty());
        conditions = List.copyOf(Objects.requireNonNullElse(conditions, List.of()));
    }

    public ExternalId effectiveExternalId() {
        return externalId.orElseGet(() -> ExternalId.of("reversal:" + transactionId));
    }
}
