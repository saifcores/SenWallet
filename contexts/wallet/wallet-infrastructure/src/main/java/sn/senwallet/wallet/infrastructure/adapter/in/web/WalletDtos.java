package sn.senwallet.wallet.infrastructure.adapter.in.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** JSON bodies. The boot application names properties in snake_case. */
public final class WalletDtos {

    private WalletDtos() {
    }

    public record RegisterRequest(String phone) {
    }

    public record CustomerResponse(UUID id, String phone, String status, String otp) {
    }

    public record VerifyRequest(String otp) {
    }

    public record VerificationResponse(
            UUID customerId,
            String kycTier,
            UUID walletId,
            String pispiAlias,
            UUID ledgerAccountId) {
    }

    public record KycRequest(String tier) {
    }

    public record KycResponse(UUID customerId, String kycTier) {
    }

    public record BalanceResponse(long posted, long pending, long available) {
    }

    public record WalletResponse(
            UUID id,
            UUID customerId,
            UUID ledgerId,
            UUID ledgerAccountId,
            String status,
            String pispiAlias,
            String kycTier,
            BalanceResponse balances) {
    }

    public record TransferRequest(UUID fromWalletId, String toPhone, Long amount, String currency) {
    }

    public record TransferResponse(
            UUID id,
            UUID fromWalletId,
            UUID toWalletId,
            long amount,
            long fee,
            String currency,
            String status,
            UUID ledgerTransactionId) {
    }

    public record HistoryItem(
            UUID eventId,
            String eventType,
            UUID transactionId,
            String externalId,
            String direction,
            long amount,
            Instant occurredAt) {
    }

    public record HistoryResponse(List<HistoryItem> entries) {
    }
}
