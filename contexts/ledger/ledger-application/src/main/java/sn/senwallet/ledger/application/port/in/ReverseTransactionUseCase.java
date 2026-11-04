package sn.senwallet.ledger.application.port.in;

import sn.senwallet.ledger.application.command.ReverseTransactionCommand;

public interface ReverseTransactionUseCase {

    PostingResult reverse(ReverseTransactionCommand command);
}
