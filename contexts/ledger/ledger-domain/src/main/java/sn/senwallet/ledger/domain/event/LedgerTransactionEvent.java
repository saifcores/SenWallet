package sn.senwallet.ledger.domain.event;

import sn.senwallet.ledger.domain.model.transaction.ExternalId;
import sn.senwallet.shared.event.DomainEvent;
import sn.senwallet.shared.id.TransactionId;

public sealed interface LedgerTransactionEvent extends DomainEvent
        permits TransactionPending, TransactionPosted, TransactionArchived {

    TransactionId transactionId();

    ExternalId externalId();
}
