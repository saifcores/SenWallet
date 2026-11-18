package sn.senwallet.payments.application.port.out;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Backoff after a rail timeout. Defaults are 30s, 1, 2, 5 and 10 minutes. */
public interface InquirySchedule {

    List<Duration> delays();

    default int maxAttempts() {
        return delays().size();
    }

    default Instant after(Instant from, int attempt) {
        var delays = delays();
        var index = Math.min(Math.max(attempt, 0), delays.size() - 1);
        return from.plus(delays.get(index));
    }
}
