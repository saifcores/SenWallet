package sn.senwallet.wallet.application.model;

import java.time.Instant;
import java.util.UUID;
import sn.senwallet.shared.id.WalletId;

/** One ledger entry that touched a wallet, kept for the history screen. */
public record HistoryEntry(
        UUID eventId,
        String eventType,
        WalletId walletId,
        UUID transactionId,
        String externalId,
        UUID ledgerId,
        String direction,
        long amount,
        Instant occurredAt) {
}
