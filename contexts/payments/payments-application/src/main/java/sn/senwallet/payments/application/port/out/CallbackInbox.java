package sn.senwallet.payments.application.port.out;

import java.util.List;

public interface CallbackInbox {

    List<StoredCallback> lockPending(int limit);

    void markProcessed(List<Long> ids);

    record StoredCallback(
            long id,
            String provider,
            String providerRef,
            String externalRef,
            String status,
            long amount,
            String creditorAlias) {
    }
}
