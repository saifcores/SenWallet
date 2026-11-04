package sn.senwallet.ledger.application.service;

import java.util.ArrayList;
import java.util.List;
import sn.senwallet.ledger.application.port.out.OutboxPort;
import sn.senwallet.ledger.domain.event.LedgerTransactionEvent;
import sn.senwallet.ledger.domain.model.transaction.LedgerTransaction;

/** Records events the handlers append; a replay appends nothing. */
final class RecordingOutbox implements OutboxPort {

    private final List<LedgerTransactionEvent> events = new ArrayList<>();

    @Override
    public void append(LedgerTransaction transaction, List<LedgerTransactionEvent> events) {
        this.events.addAll(events);
    }

    List<LedgerTransactionEvent> events() {
        return List.copyOf(events);
    }

    void clear() {
        events.clear();
    }
}
