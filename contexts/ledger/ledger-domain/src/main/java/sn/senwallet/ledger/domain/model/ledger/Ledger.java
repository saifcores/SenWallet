package sn.senwallet.ledger.domain.model.ledger;

import java.time.Instant;
import java.util.Objects;
import sn.senwallet.shared.id.LedgerId;

/** One ledger per legal entity and environment; accounts and transactions never cross ledgers. */
public record Ledger(LedgerId id, String name, LedgerEnvironment environment, Instant createdAt) {

    public Ledger {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(createdAt, "createdAt");
        if (name.isBlank()) {
            throw new IllegalArgumentException("ledger name must not be blank");
        }
    }

    public static Ledger create(String name, LedgerEnvironment environment, Instant now) {
        return new Ledger(LedgerId.newId(), name, environment, now);
    }
}
