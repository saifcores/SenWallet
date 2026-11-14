package sn.senwallet.simulators.mno;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MnoDtos {

    private MnoDtos() {
    }

    public record MoneyRequest(
            String externalId,
            String phone,
            Long amount,
            String currency) {
    }

    public record TransactionResponse(
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

    public record FloatResponse(String provider, long available) {
    }

    public record SetFloatRequest(Long available) {
    }

    public record SettlementFile(
            String date,
            String provider,
            List<SettlementLine> lines) {
    }

    public record SettlementLine(
            String externalId,
            String type,
            long amount,
            String status) {
    }

    public record CallbackBody(
            UUID id,
            String externalId,
            String type,
            String status,
            String phone,
            long amount,
            String currency,
            String provider) {
    }
}
