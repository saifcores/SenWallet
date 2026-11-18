package sn.senwallet.payments.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import sn.senwallet.payments.domain.exception.IllegalPaymentTransition;

class PaymentOrderTest {

    private static final Instant AT = Instant.parse("2026-11-16T12:00:00Z");

    @Test
    void successAfterUnknownPostsForward() {
        var order = held().unknown(AT.plusSeconds(30), AT).completed(AT.plusSeconds(40));

        assertThat(order.state()).isEqualTo(PaymentOrder.State.COMPLETED);
        assertThat(order.ledgerTxnId()).isNotNull();
    }

    @Test
    void aSecondSuccessIsRejected() {
        var completed = held().submitted("ref", null, AT).completed(AT);

        assertThatThrownBy(() -> completed.completed(AT.plusSeconds(1)))
                .isInstanceOf(IllegalPaymentTransition.class);
    }

    @Test
    void failureDoesNotLeaveSubmittedForALaterSuccess() {
        var failed = held().submitted("ref", null, AT).failed(AT);

        assertThatThrownBy(() -> failed.completed(AT.plusSeconds(1)))
                .isInstanceOf(IllegalPaymentTransition.class);
    }

    @Test
    void unknownKeepsTheHoldAndFiveMissesReachManualReview() {
        var unknown = held().unknown(AT.plusSeconds(30), AT);
        var cursor = unknown;
        for (var i = 0; i < 4; i++) {
            cursor = cursor.inquiryMissed(AT.plusSeconds(60L * (i + 1)), 5, AT);
            assertThat(cursor.state()).isEqualTo(PaymentOrder.State.UNKNOWN);
        }
        var review = cursor.inquiryMissed(null, 5, AT);

        assertThat(review.state()).isEqualTo(PaymentOrder.State.MANUAL_REVIEW);
        assertThat(review.attempts()).isEqualTo(5);
        assertThat(review.ledgerTxnId()).isEqualTo(unknown.ledgerTxnId());
    }

    @Test
    void succeededCallbackWinsOverAnEarlierFailure() {
        var chosen = CallbackPreference.prefer(List.of(RailStatus.FAILED, RailStatus.SUCCEEDED));

        assertThat(chosen).isEqualTo(RailStatus.SUCCEEDED);
    }

    @Test
    void duplicateSuccessStaysSuccess() {
        assertThat(CallbackPreference.prefer(List.of(RailStatus.SUCCEEDED, RailStatus.SUCCEEDED)))
                .isEqualTo(RailStatus.SUCCEEDED);
    }

    @Test
    void externalIdsMatchTheLedgerScenario() {
        var id = UUID.fromString("00000000-0000-4000-8000-000000000001");
        var cashout = PaymentOrder.open(id, PaymentOrder.Type.CASHOUT, UUID.randomUUID(), 50_000, 0,
                PaymentOrder.Provider.ORANGE, "+221770000000", null, AT);

        assertThat(cashout.externalId()).isEqualTo("cashout:" + id);
    }

    private static PaymentOrder held() {
        return PaymentOrder.open(UUID.randomUUID(), PaymentOrder.Type.CASHOUT, UUID.randomUUID(), 50_000, 0,
                PaymentOrder.Provider.ORANGE, "+221770000000", null, AT)
                .holdPlaced(UUID.randomUUID(), AT);
    }
}
