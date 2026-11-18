package sn.senwallet.payments.application.port.out;

import java.util.List;
import java.util.UUID;
import sn.senwallet.payments.domain.model.PaymentOrder;

public interface LedgerPort {

    RailAccounts ensureRails(UUID ledgerId, UUID feeAccountId);

    /** Places a pending transaction. Replaying the same external id returns the existing one. */
    UUID place(Hold hold);

    /** Posts immediately. Replaying the same external id returns the existing one. */
    UUID postNow(Hold hold);

    void post(UUID transactionId);

    void archive(UUID transactionId);

    /** {@code pending}, {@code posted} or {@code archived}. */
    String status(UUID transactionId);

    java.util.Optional<RailAccounts> currentRails();

    record Line(UUID accountId, String direction, long amount) {
    }

    record Condition(UUID accountId, String balance, String operator, long amount) {
    }

    record Hold(
            UUID ledgerId,
            String externalId,
            String description,
            List<Line> lines,
            List<Condition> conditions) {
    }

    record RailAccounts(
            UUID ledgerId,
            UUID feeAccountId,
            UUID waveFloatId,
            UUID orangeFloatId,
            UUID waveReceivableId,
            UUID orangeReceivableId,
            UUID compensationId,
            UUID suspenseId) {

        public UUID floatOf(PaymentOrder.Provider provider) {
            return provider == PaymentOrder.Provider.ORANGE ? orangeFloatId : waveFloatId;
        }

        public UUID receivableOf(PaymentOrder.Provider provider) {
            return provider == PaymentOrder.Provider.ORANGE ? orangeReceivableId : waveReceivableId;
        }
    }
}
