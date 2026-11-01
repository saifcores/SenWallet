package sn.senwallet.ledger.application.command;

import java.util.Map;
import java.util.Objects;
import sn.senwallet.ledger.domain.model.account.BalanceMode;
import sn.senwallet.ledger.domain.model.account.NormalBalance;
import sn.senwallet.shared.id.LedgerId;
import sn.senwallet.shared.money.Currency;

public record OpenAccountCommand(
        LedgerId ledgerId,
        String name,
        NormalBalance normalBalance,
        Currency currency,
        BalanceMode balanceMode,
        Map<String, String> metadata) {

    public OpenAccountCommand {
        Objects.requireNonNull(ledgerId, "ledgerId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(normalBalance, "normalBalance");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(balanceMode, "balanceMode");
        metadata = Map.copyOf(Objects.requireNonNullElse(metadata, Map.of()));
    }
}
