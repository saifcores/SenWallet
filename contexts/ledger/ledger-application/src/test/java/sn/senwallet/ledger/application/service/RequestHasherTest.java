package sn.senwallet.ledger.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static sn.senwallet.ledger.domain.model.transaction.TransactionStatus.POSTED;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import sn.senwallet.ledger.application.command.PostTransactionCommand;
import sn.senwallet.ledger.domain.model.transaction.Entry;
import sn.senwallet.ledger.domain.model.transaction.ExternalId;

class RequestHasherTest {

    private final LedgerFixture f = new LedgerFixture();

    @Test
    void sameRequestSameHash() {
        assertThat(RequestHasher.hash(f.fund("a", f.adama, 10)))
                .isEqualTo(RequestHasher.hash(f.fund("a", f.adama, 10)));
    }

    @Test
    void anyChangeChangesTheHash() {
        var base = RequestHasher.hash(f.fund("a", f.adama, 10));

        assertThat(RequestHasher.hash(f.fund("a", f.adama, 11))).isNotEqualTo(base);
        assertThat(RequestHasher.hash(f.fund("a", f.binta, 10))).isNotEqualTo(base);
        assertThat(RequestHasher.hash(f.fund("b", f.adama, 10))).isNotEqualTo(base);
        assertThat(RequestHasher.hash(f.transfer("a", f.treasury, f.adama, 10, POSTED))).isNotEqualTo(base);
    }

    @Test
    void metadataOrderDoesNotMatterButEntryOrderDoes() {
        var command = f.fund("a", f.adama, 10);
        var withMetadata = with(command, command.entries(), Map.of("k1", "v1", "k2", "v2"), null);
        var sameMetadata = with(command, command.entries(), Map.of("k2", "v2", "k1", "v1"), null);
        var swapped = with(command, List.of(command.entries().get(1), command.entries().get(0)), Map.of(), null);

        assertThat(RequestHasher.hash(withMetadata)).isEqualTo(RequestHasher.hash(sameMetadata));
        assertThat(RequestHasher.hash(swapped)).isNotEqualTo(RequestHasher.hash(command));
    }

    @Test
    void fieldBoundariesCannotBeShifted() {
        var command = f.fund("a", f.adama, 10);

        assertThat(RequestHasher.hash(with(command, command.entries(), Map.of("ab", "c"), null)))
                .isNotEqualTo(RequestHasher.hash(with(command, command.entries(), Map.of("a", "bc"), null)));
        assertThat(RequestHasher.hash(with(command, command.entries(), Map.of(), "")))
                .isEqualTo(RequestHasher.hash(command));
    }

    private static PostTransactionCommand with(
            PostTransactionCommand base, List<Entry> entries,
            Map<String, String> metadata, String description) {
        return new PostTransactionCommand(base.ledgerId(), ExternalId.of(base.externalId().value()), base.status(),
                entries, base.conditions(), description, metadata);
    }
}
