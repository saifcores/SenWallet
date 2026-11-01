package sn.senwallet.ledger.domain.model.condition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import sn.senwallet.ledger.domain.model.account.Balances;
import sn.senwallet.ledger.domain.model.account.NormalBalance;
import sn.senwallet.shared.id.AccountId;
import sn.senwallet.shared.money.Money;

class BalanceConditionTest {

    // Credit-normal wallet: posted 100, pending 120 (incoming 50, outgoing 30), available 70.
    private static final Balances WALLET =
            new Balances(Money.xof(0), Money.xof(100), Money.xof(30), Money.xof(50), 0);

    static Stream<Arguments> cases() {
        return Stream.of(BalanceType.values()).flatMap(type -> {
            long value = switch (type) {
                case POSTED -> 100;
                case PENDING -> 120;
                case AVAILABLE -> 70;
            };
            return Stream.of(
                    Arguments.of(type, Operator.GT, value - 1, true),
                    Arguments.of(type, Operator.GT, value, false),
                    Arguments.of(type, Operator.GTE, value, true),
                    Arguments.of(type, Operator.GTE, value + 1, false),
                    Arguments.of(type, Operator.EQ, value, true),
                    Arguments.of(type, Operator.EQ, value + 1, false),
                    Arguments.of(type, Operator.LTE, value, true),
                    Arguments.of(type, Operator.LTE, value - 1, false),
                    Arguments.of(type, Operator.LT, value + 1, true),
                    Arguments.of(type, Operator.LT, value, false));
        });
    }

    @ParameterizedTest(name = "{0} {1} {2} -> {3}")
    @MethodSource("cases")
    void evaluatesEveryOperatorOnEveryBalance(BalanceType type, Operator operator, long threshold, boolean expected) {
        var condition = BalanceCondition.of(AccountId.newId(), type, operator, Money.xof(threshold));

        assertThat(condition.isSatisfiedBy(WALLET, NormalBalance.CREDIT)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} {1} {2} -> {3}")
    @MethodSource("cases")
    void readsTheBalanceOfADebitNormalAccountTheSameWay(
            BalanceType type, Operator operator, long threshold, boolean expected) {
        var mirrored = new Balances(
                WALLET.postedCredits(), WALLET.postedDebits(), WALLET.pendingCredits(), WALLET.pendingDebits(), 0);
        var condition = BalanceCondition.of(AccountId.newId(), type, operator, Money.xof(threshold));

        assertThat(condition.isSatisfiedBy(mirrored, NormalBalance.DEBIT)).isEqualTo(expected);
    }
}
