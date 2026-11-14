package sn.senwallet.simulators.mno;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MnoStore {

    private final Map<UUID, StoredTx> byId = new ConcurrentHashMap<>();
    private final AtomicLong floatAvailable;

    public MnoStore(@Value("${simulator.initial-float:100000000}") long initialFloat) {
        this.floatAvailable = new AtomicLong(initialFloat);
    }

    public StoredTx save(StoredTx tx) {
        byId.put(tx.id(), tx);
        return tx;
    }

    public Optional<StoredTx> find(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<StoredTx> updateStatus(UUID id, String status) {
        return Optional.ofNullable(byId.computeIfPresent(id, (key, tx) -> new StoredTx(
                tx.id(),
                tx.externalId(),
                tx.type(),
                status,
                tx.phone(),
                tx.amount(),
                tx.currency(),
                tx.provider(),
                tx.createdAt())));
    }

    public long floatAvailable() {
        return floatAvailable.get();
    }

    public void setFloat(long available) {
        floatAvailable.set(available);
    }

    public boolean tryDebitFloat(long amount) {
        while (true) {
            long current = floatAvailable.get();
            if (current < amount) {
                return false;
            }
            if (floatAvailable.compareAndSet(current, current - amount)) {
                return true;
            }
        }
    }

    public void creditFloat(long amount) {
        floatAvailable.addAndGet(amount);
    }

    public List<StoredTx> onDate(LocalDate date) {
        List<StoredTx> result = new ArrayList<>();
        for (StoredTx tx : byId.values()) {
            LocalDate created = LocalDate.ofInstant(tx.createdAt(), ZoneOffset.UTC);
            if (created.equals(date)) {
                result.add(tx);
            }
        }
        return result;
    }

    public record StoredTx(
            UUID id,
            String externalId,
            String type,
            String status,
            String phone,
            long amount,
            String currency,
            String provider,
            Instant createdAt) {
    }
}
