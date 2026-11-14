package sn.senwallet.simulators.bank;

import java.time.Instant;
import java.util.UUID;

public final class BankDtos {

    private BankDtos() {
    }

    public record CreditRequest(
            String account,
            String reference,
            Long amount,
            String currency,
            String description) {
    }

    public record CreditResponse(
            UUID id,
            String account,
            String reference,
            long amount,
            String currency,
            Instant createdAt) {
    }

    public record StatementResponse(
            String bank,
            String account,
            String date,
            String objectKey,
            String bucket,
            int lineCount) {
    }
}
