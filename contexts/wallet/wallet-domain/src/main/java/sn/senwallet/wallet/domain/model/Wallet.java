package sn.senwallet.wallet.domain.model;

import java.util.Objects;
import sn.senwallet.shared.id.AccountId;
import sn.senwallet.shared.id.CustomerId;
import sn.senwallet.shared.id.WalletId;

/**
 * A customer's ledger account plus the local PI-SPI alias. The alias is the
 * phone; PI-SPI itself is not called.
 */
public record Wallet(
        WalletId id,
        CustomerId customerId,
        WalletStatus status,
        AccountId ledgerAccountId,
        Phone alias) {

    public Wallet {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(alias, "alias");
        if (status == WalletStatus.ACTIVE) {
            Objects.requireNonNull(ledgerAccountId, "ledgerAccountId");
        }
    }

    public static Wallet active(WalletId id, CustomerId customerId, AccountId ledgerAccountId, Phone alias) {
        return new Wallet(id, customerId, WalletStatus.ACTIVE, ledgerAccountId, alias);
    }
}
