package sn.senwallet.wallet.domain.model;

import java.util.Objects;
import java.util.UUID;
import sn.senwallet.shared.id.WalletId;
import sn.senwallet.shared.money.Money;

/** A P2P that was accepted or refused by the ledger. */
public record Transfer(
        TransferId id,
        WalletId fromWalletId,
        WalletId toWalletId,
        Money amount,
        Money fee,
        TransferStatus status,
        UUID ledgerTransactionId) {

    public Transfer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(fromWalletId, "fromWalletId");
        Objects.requireNonNull(toWalletId, "toWalletId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(fee, "fee");
        Objects.requireNonNull(status, "status");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (fee.isNegative()) {
            throw new IllegalArgumentException("fee must not be negative");
        }
        if (fromWalletId.equals(toWalletId)) {
            throw new IllegalArgumentException("sender and recipient are the same wallet");
        }
        if (status == TransferStatus.POSTED) {
            Objects.requireNonNull(ledgerTransactionId, "ledgerTransactionId");
        }
    }

    public static Transfer posted(
            TransferId id,
            WalletId fromWalletId,
            WalletId toWalletId,
            Money amount,
            Money fee,
            UUID ledgerTransactionId) {
        return new Transfer(id, fromWalletId, toWalletId, amount, fee, TransferStatus.POSTED, ledgerTransactionId);
    }

    public static Transfer rejected(
            TransferId id,
            WalletId fromWalletId,
            WalletId toWalletId,
            Money amount,
            Money fee) {
        return new Transfer(id, fromWalletId, toWalletId, amount, fee, TransferStatus.REJECTED, null);
    }
}
