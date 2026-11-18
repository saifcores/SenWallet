package sn.senwallet.payments.application.port.out;

import java.util.UUID;

public interface IdempotencyStore {

    Begin begin(UUID key, String requestHash);

    void finish(UUID key, int status, String body);

    record Begin(UUID orderId, Integer responseStatus, String responseBody) {

        public boolean finished() {
            return responseStatus != null;
        }
    }
}
