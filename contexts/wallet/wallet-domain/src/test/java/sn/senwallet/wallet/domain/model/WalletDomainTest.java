package sn.senwallet.wallet.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import sn.senwallet.shared.id.AccountId;
import sn.senwallet.shared.id.CustomerId;
import sn.senwallet.shared.id.WalletId;
import sn.senwallet.shared.money.Money;
import sn.senwallet.wallet.domain.exception.InvalidOtpException;
import sn.senwallet.wallet.domain.model.P2pPosting.Direction;

class WalletDomainTest {

    private static final Instant INSTRUCTION = Instant.parse("2026-11-02T00:00:00Z");
    private static final Instant LATER = Instant.parse("2026-12-01T00:00:00Z");
    private static final Instant BEFORE = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    void feeOfFiftyThousandIsFiveHundred() {
        var schedule = new FeeSchedule(new BigDecimal("0.01"), INSTRUCTION);

        assertThat(schedule.feeFor(Money.xof(50_000))).isEqualTo(Money.xof(500));
    }

    @Test
    void feeRoundsHalfUpAndMayBeZero() {
        var schedule = new FeeSchedule(new BigDecimal("0.01"), INSTRUCTION);

        assertThat(schedule.feeFor(Money.xof(50))).isEqualTo(Money.xof(1));
        assertThat(schedule.feeFor(Money.xof(1))).isEqualTo(Money.xof(0));
    }

    @Test
    void policyLookupRespectsValidFrom() {
        var current = new LimitPolicy(KycTier.N1, Money.xof(4_000_000), INSTRUCTION);
        var next = new LimitPolicy(KycTier.N1, Money.xof(5_000_000), LATER);
        var otherTier = new LimitPolicy(KycTier.N2, Money.xof(1), INSTRUCTION);
        var policies = List.of(next, current, otherTier);

        assertThat(LimitPolicy.effective(policies, KycTier.N1, INSTRUCTION)).isEqualTo(current);
        assertThat(LimitPolicy.effective(policies, KycTier.N1, LATER)).isEqualTo(next);
        assertThatThrownBy(() -> LimitPolicy.effective(policies, KycTier.N1, BEFORE))
                .isInstanceOf(IllegalArgumentException.class);

        var early = new FeeSchedule(new BigDecimal("0.02"), BEFORE);
        var p2p = new FeeSchedule(new BigDecimal("0.01"), INSTRUCTION);
        assertThat(FeeSchedule.effective(List.of(early, p2p), INSTRUCTION)).isEqualTo(p2p);
    }

    @Test
    void p2pPostingDebitsTheFeeAndGuardsTheCap() {
        var sender = UUID.randomUUID();
        var recipient = UUID.randomUUID();
        var fees = UUID.randomUUID();
        var transferId = TransferId.newId();

        var posting = P2pPosting.of(transferId, sender, recipient, fees, Money.xof(50_000), Money.xof(500),
                Money.xof(4_000_000));

        assertThat(posting.externalId()).isEqualTo("p2p:" + transferId);
        assertThat(posting.entries()).containsExactly(
                new P2pPosting.PostingLine(sender, Direction.DEBIT, 50_500),
                new P2pPosting.PostingLine(recipient, Direction.CREDIT, 50_000),
                new P2pPosting.PostingLine(fees, Direction.CREDIT, 500));
        assertThat(posting.conditions()).containsExactly(
                new P2pPosting.PostingCondition(sender, "available", "gte", 0),
                new P2pPosting.PostingCondition(recipient, "pending", "lte", 4_000_000));
    }

    @Test
    void zeroFeeOmitsTheFeeEntry() {
        var sender = UUID.randomUUID();
        var recipient = UUID.randomUUID();

        var posting = P2pPosting.of(TransferId.newId(), sender, recipient, null, Money.xof(40), Money.xof(0),
                Money.xof(4_000_000));

        assertThat(posting.entries()).hasSize(2);
        assertThat(posting.entries().getFirst().amount()).isEqualTo(40);
    }

    @Test
    void verificationSetsTierN1AndClearsTheOtp() {
        var customer = Customer.register(CustomerId.newId(), new Phone("+221770000001"), "123456");

        var verified = customer.verify("123456");

        assertThat(verified.status()).isEqualTo(CustomerStatus.VERIFIED);
        assertThat(verified.tier()).isEqualTo(KycTier.N1);
        assertThat(verified.otp()).isNull();
        assertThatThrownBy(() -> customer.verify("000000")).isInstanceOf(InvalidOtpException.class);
    }

    @Test
    void phoneMustBeE164() {
        assertThatThrownBy(() -> new Phone("770000001")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void activeWalletRequiresALedgerAccount() {
        var wallet = Wallet.active(WalletId.newId(), CustomerId.newId(), AccountId.newId(), new Phone("+221770000001"));

        assertThat(wallet.status()).isEqualTo(WalletStatus.ACTIVE);
        assertThat(wallet.ledgerAccountId()).isNotNull();
    }
}
