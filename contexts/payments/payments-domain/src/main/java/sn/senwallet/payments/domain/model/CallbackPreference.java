package sn.senwallet.payments.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * When several callbacks for one order are still unprocessed, a success wins
 * over an earlier failure (out-of-order delivery).
 */
public final class CallbackPreference {

    private CallbackPreference() {
    }

    public static RailStatus prefer(List<RailStatus> statuses) {
        Objects.requireNonNull(statuses, "statuses");
        var seenFailure = false;
        var seenPending = false;
        for (var status : statuses) {
            Objects.requireNonNull(status, "status");
            if (status == RailStatus.SUCCEEDED) {
                return RailStatus.SUCCEEDED;
            }
            if (status == RailStatus.FAILED) {
                seenFailure = true;
            } else if (status == RailStatus.PENDING) {
                seenPending = true;
            }
        }
        if (seenFailure) {
            return RailStatus.FAILED;
        }
        if (seenPending) {
            return RailStatus.PENDING;
        }
        return RailStatus.UNKNOWN;
    }
}
