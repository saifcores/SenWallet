package sn.senwallet.ledger.application.port.out;

import java.time.Instant;

public interface ClockPort {

    Instant now();
}
