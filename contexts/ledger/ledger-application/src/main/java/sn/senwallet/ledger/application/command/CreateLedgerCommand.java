package sn.senwallet.ledger.application.command;

import java.util.Objects;
import sn.senwallet.ledger.domain.model.ledger.LedgerEnvironment;

public record CreateLedgerCommand(String name, LedgerEnvironment environment) {

    public CreateLedgerCommand {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(environment, "environment");
    }
}
