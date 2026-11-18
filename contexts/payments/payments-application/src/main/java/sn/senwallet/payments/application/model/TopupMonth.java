package sn.senwallet.payments.application.model;

import java.time.Instant;
import java.time.ZoneId;

/** Calendar month of a top-up, in the UEMOA time zone. */
public final class TopupMonth {

    public static final ZoneId ZONE = ZoneId.of("Africa/Dakar");

    private TopupMonth() {
    }

    public static Instant start(Instant at) {
        var first = at.atZone(ZONE).toLocalDate().withDayOfMonth(1);
        return first.atStartOfDay(ZONE).toInstant();
    }

    public static Instant end(Instant at) {
        return start(at).atZone(ZONE).plusMonths(1).toInstant();
    }
}
