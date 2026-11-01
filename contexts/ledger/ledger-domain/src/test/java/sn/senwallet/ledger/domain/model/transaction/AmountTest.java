package sn.senwallet.ledger.domain.model.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sn.senwallet.shared.money.Currency;
import sn.senwallet.shared.money.Money;

class AmountTest {

    @Test
    void wrapsAPositiveXofAmount() {
        var amount = Amount.xof(5_000);

        assertThat(amount.value()).isEqualTo(Money.xof(5_000));
        assertThat(amount.currency()).isEqualTo(Currency.XOF);
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, -1, -50_000 })
    void rejectsZeroAndNegativeAmounts(long value) {
        assertThatThrownBy(() -> Amount.xof(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("> 0");
    }

    @Test
    void rejectsFractionalFrancsThroughMoney() {
        assertThatThrownBy(() -> Amount.of(Money.xof(new BigDecimal("0.5"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalAmountsAreEqual() {
        assertThat(Amount.of(Money.xof(new BigDecimal("100.00")))).isEqualTo(Amount.xof(100));
    }
}
