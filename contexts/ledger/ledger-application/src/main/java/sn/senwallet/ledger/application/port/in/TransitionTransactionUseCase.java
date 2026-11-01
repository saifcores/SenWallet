package sn.senwallet.ledger.application.port.in;

import sn.senwallet.ledger.application.command.TransitionCommand;

public interface TransitionTransactionUseCase {

    TransitionResult transition(TransitionCommand command);
}
