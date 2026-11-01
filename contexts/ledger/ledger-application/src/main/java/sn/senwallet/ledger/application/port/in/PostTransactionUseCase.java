package sn.senwallet.ledger.application.port.in;

import sn.senwallet.ledger.application.command.PostTransactionCommand;

public interface PostTransactionUseCase {

    /**
     * Posts a transaction, or returns the stored one if the same request was
     * already posted under this external id.
     */
    PostingResult post(PostTransactionCommand command);
}
