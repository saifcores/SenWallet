package sn.senwallet.wallet.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import sn.senwallet.shared.money.Money;

/**
 * The ledger posting for one P2P. Conditions are expressed for the ledger;
 * the wallet does not judge balances itself.
 */
public record P2pPosting(String externalId, List<PostingLine> entries, List<PostingCondition> conditions) {

    public P2pPosting {
        Objects.requireNonNull(externalId, "externalId");
        if (externalId.isBlank()) {
            throw new IllegalArgumentException("externalId must not be blank");
        }
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        conditions = List.copyOf(Objects.requireNonNull(conditions, "conditions"));
        if (entries.size() < 2) {
            throw new IllegalArgumentException("a posting needs at least two entries");
        }
    }

    public enum Direction {
        DEBIT,
        CREDIT
    }

    public record PostingLine(UUID accountId, Direction direction, long amount) {

        public PostingLine {
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(direction, "direction");
            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }
        }
    }

    public record PostingCondition(UUID accountId, String balance, String operator, long amount) {

        public PostingCondition {
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(balance, "balance");
            Objects.requireNonNull(operator, "operator");
            if (amount < 0) {
                throw new IllegalArgumentException("condition amount must not be negative");
            }
        }
    }

    public static P2pPosting of(
            TransferId transferId,
            UUID senderAccountId,
            UUID recipientAccountId,
            UUID feeAccountId,
            Money amount,
            Money fee,
            Money holdingCap) {
        Objects.requireNonNull(transferId, "transferId");
        Objects.requireNonNull(senderAccountId, "senderAccountId");
        Objects.requireNonNull(recipientAccountId, "recipientAccountId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(fee, "fee");
        Objects.requireNonNull(holdingCap, "holdingCap");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (fee.isNegative()) {
            throw new IllegalArgumentException("fee must not be negative");
        }
        var lines = new java.util.ArrayList<PostingLine>();
        lines.add(new PostingLine(senderAccountId, Direction.DEBIT, amount.plus(fee).toMinorUnits()));
        lines.add(new PostingLine(recipientAccountId, Direction.CREDIT, amount.toMinorUnits()));
        if (fee.isPositive()) {
            Objects.requireNonNull(feeAccountId, "feeAccountId");
            lines.add(new PostingLine(feeAccountId, Direction.CREDIT, fee.toMinorUnits()));
        }
        var conditions = List.of(
                new PostingCondition(senderAccountId, "available", "gte", 0),
                new PostingCondition(recipientAccountId, "pending", "lte", holdingCap.toMinorUnits()));
        return new P2pPosting("p2p:" + transferId, lines, conditions);
    }
}
