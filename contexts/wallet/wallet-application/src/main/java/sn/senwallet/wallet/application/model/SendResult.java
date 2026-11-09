package sn.senwallet.wallet.application.model;

import sn.senwallet.wallet.domain.model.Transfer;

/** Outcome of {@code POST /transfers}, including a replay of a finished key. */
public sealed interface SendResult {

    Transfer transfer();

    record Accepted(Transfer transfer) implements SendResult {
    }

    record Refused(Transfer transfer, int status, String code, String detail) implements SendResult {
    }
}
