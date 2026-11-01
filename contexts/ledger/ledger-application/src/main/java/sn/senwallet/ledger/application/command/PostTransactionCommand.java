package sn.senwallet.ledger.application.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import sn.senwallet.ledger.domain.model.condition.BalanceCondition;
import sn.senwallet.ledger.domain.model.transaction.Entry;
import sn.senwallet.ledger.domain.model.transaction.ExternalId;
import sn.senwallet.ledger.domain.model.transaction.TransactionStatus;
import sn.senwallet.shared.id.LedgerId;

public record PostTransactionCommand(
        LedgerId ledgerId,
        ExternalId externalId,
        TransactionStatus status,
        List<Entry> entries,
        List<BalanceCondition> conditions,
        String description,
        Map<String, String> metadata) {

    public PostTransactionCommand {
        Objects.requireNonNull(ledgerId, "ledgerId");
        Objects.requireNonNull(externalId, "externalId");
        Objects.requireNonNull(status, "status");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        conditions = List.copyOf(Objects.requireNonNullElse(conditions, List.of()));
        metadata = Map.copyOf(Objects.requireNonNullElse(metadata, Map.of()));
    }
}
