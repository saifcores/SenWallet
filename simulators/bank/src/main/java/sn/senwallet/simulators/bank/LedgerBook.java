package sn.senwallet.simulators.bank;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class LedgerBook {

    private final Map<String, List<Credit>> byAccount = new ConcurrentHashMap<>();

    public Credit credit(
            String account,
            String reference,
            long amount,
            String currency,
            String description) {
        Credit credit = new Credit(
                UUID.randomUUID(),
                account,
                reference,
                amount,
                currency,
                description == null ? "" : description,
                Instant.now());
        byAccount.compute(account, (key, existing) -> {
            List<Credit> next = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            next.add(credit);
            return next;
        });
        return credit;
    }

    public List<Credit> creditsOn(String account, LocalDate date) {
        List<Credit> credits = byAccount.getOrDefault(account, List.of());
        List<Credit> result = new ArrayList<>();
        for (Credit credit : credits) {
            LocalDate created = LocalDate.ofInstant(credit.createdAt(), ZoneOffset.UTC);
            if (created.equals(date)) {
                result.add(credit);
            }
        }
        return result;
    }

    public record Credit(
            UUID id,
            String account,
            String reference,
            long amount,
            String currency,
            String description,
            Instant createdAt) {
    }
}
