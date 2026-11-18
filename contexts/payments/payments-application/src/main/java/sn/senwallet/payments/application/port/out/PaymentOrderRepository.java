package sn.senwallet.payments.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import sn.senwallet.payments.domain.model.PaymentOrder;

public interface PaymentOrderRepository {

    void insert(PaymentOrder order);

    void update(PaymentOrder order);

    Optional<PaymentOrder> findById(UUID id);

    Optional<PaymentOrder> findByProviderRef(String providerRef);

    /**
     * Inserts {@code order} when this wallet's top-ups in {@code [start, end)}
     * plus the new amount stay within {@code cap}. Returns false and writes
     * nothing when they would not.
     */
    boolean insertWithinMonthlyCap(PaymentOrder order, long cap, Instant start, Instant end);

    /** @return true when this caller is the one that may call the rail */
    boolean claimRail(UUID orderId);

    List<PaymentOrder> lockDue(int limit);
}
