package sn.senwallet.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void xofHasExponentZero() {
        assertThat(Currency.XOF.exponent()).isZero();
        assertThat(Money.xof(5_000).amount()).isEqualByComparingTo("5000");
        assertThat(Money.xof(5_000).amount().scale()).isZero();
    }

    @Test
    void addsAndSubtractsExactly() {
        var balance = Money.xof(49_500).plus(Money.xof(20_000)).minus(Money.xof(50_500));

        assertThat(balance).isEqualTo(Money.xof(19_000));
    }

    @Test
    void canGoNegativeSoCallersCanDetectOverdraft() {
        var result = Money.xof(1_000).minus(Money.xof(1_500));

        assertThat(result.isNegative()).isTrue();
        assertThat(result.negate()).isEqualTo(Money.xof(500));
    }

    @Test
    void rejectsFractionalFrancs() {
        assertThatThrownBy(() -> Money.xof(new BigDecimal("100.5")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100.5");
    }

    @Test
    void normalisesScaleSoEqualAmountsAreEqual() {
        assertThat(Money.xof(new BigDecimal("100.00"))).isEqualTo(Money.xof(100));
        assertThat(Money.xof(new BigDecimal("1E+3"))).isEqualTo(Money.xof(1_000));
    }

    @Test
    void doesNotOverflowBeyondLong() {
        var sum = Money.xof(Long.MAX_VALUE).plus(Money.xof(1));

        assertThat(sum.amount()).isEqualByComparingTo("9223372036854775808");
        assertThatThrownBy(sum::toMinorUnits).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void convertsToMinorUnitsForStorage() {
        assertThat(Money.xof(50_500).toMinorUnits()).isEqualTo(50_500L);
    }

    @Test
    void comparesByAmount() {
        assertThat(Money.xof(1)).isGreaterThan(Money.zero(Currency.XOF));
        assertThat(Money.zero(Currency.XOF).isZero()).isTrue();
        assertThat(Money.xof(1).isPositive()).isTrue();
    }

    @Test
    void rejectsMissingAmountOrCurrency() {
        assertThatThrownBy(() -> new Money(null, Currency.XOF))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void printsPlainAmountAndCurrency() {
        assertThat(Money.xof(50_000)).hasToString("50000 XOF");
    }
}
