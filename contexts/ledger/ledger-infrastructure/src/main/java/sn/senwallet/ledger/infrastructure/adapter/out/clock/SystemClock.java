package sn.senwallet.ledger.infrastructure.adapter.out.clock;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import sn.senwallet.ledger.application.port.out.ClockPort;

/** Truncated to microseconds, the precision of {@code timestamptz}. */
public class SystemClock implements ClockPort {

    private final Clock clock;

    public SystemClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
