package sn.senwallet.wallet.application.port.out;

import java.time.Instant;
import java.util.UUID;
import sn.senwallet.wallet.application.model.SendResult;
import sn.senwallet.wallet.domain.model.TransferId;

/**
 * One row per {@code Idempotency-Key}. The transfer id is chosen on the first
 * claim so a retry posts {@code p2p:{transferId}} again.
 */
public interface TransferIdempotency {

    Begin begin(UUID key, String requestHash);

    void finish(UUID key, SendResult result);

    record Begin(TransferId transferId, SendResult previous) {

        public boolean finished() {
            return previous != null;
        }
    }
}
