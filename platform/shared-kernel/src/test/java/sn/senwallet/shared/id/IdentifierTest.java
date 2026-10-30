package sn.senwallet.shared.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentifierTest {

    @Test
    void equalWhenSameUuid() {
        var uuid = UUID.randomUUID();

        assertThat(new AccountId(uuid)).isEqualTo(AccountId.of(uuid.toString()));
        assertThat(new AccountId(uuid)).hasToString(uuid.toString());
    }

    @Test
    void newIdsAreDistinct() {
        assertThat(WalletId.newId()).isNotEqualTo(WalletId.newId());
        assertThat(TransactionId.newId()).isNotEqualTo(TransactionId.newId());
        assertThat(CustomerId.newId()).isNotEqualTo(CustomerId.newId());
        assertThat(LedgerId.newId()).isNotEqualTo(LedgerId.newId());
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new LedgerId(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsMalformedValue() {
        assertThatThrownBy(() -> AccountId.of("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
