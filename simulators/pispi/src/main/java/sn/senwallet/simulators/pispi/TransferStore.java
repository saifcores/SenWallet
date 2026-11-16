package sn.senwallet.simulators.pispi;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class TransferStore {

    private final Map<UUID, StoredTransfer> byId = new ConcurrentHashMap<>();
    private final Map<String, UUID> byEndToEnd = new ConcurrentHashMap<>();

    public StoredTransfer save(StoredTransfer transfer) {
        byId.put(transfer.id(), transfer);
        byEndToEnd.put(transfer.endToEndId(), transfer.id());
        return transfer;
    }

    public Optional<StoredTransfer> find(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<StoredTransfer> updateStatus(UUID id, String status) {
        return Optional.ofNullable(byId.computeIfPresent(id, (key, transfer) ->
                new StoredTransfer(
                        transfer.id(),
                        transfer.endToEndId(),
                        status,
                        transfer.direction(),
                        transfer.debtorAlias(),
                        transfer.creditorAlias(),
                        transfer.amount(),
                        transfer.currency(),
                        transfer.createdAt())));
    }

    public List<StoredTransfer> onDate(LocalDate date) {
        List<StoredTransfer> result = new ArrayList<>();
        for (StoredTransfer transfer : byId.values()) {
            LocalDate created = LocalDate.ofInstant(transfer.createdAt(), ZoneOffset.UTC);
            if (created.equals(date)) {
                result.add(transfer);
            }
        }
        return result;
    }

    public record StoredTransfer(
            UUID id,
            String endToEndId,
            String status,
            String direction,
            String debtorAlias,
            String creditorAlias,
            long amount,
            String currency,
            Instant createdAt) {
    }
}
