package sn.senwallet.simulators.pispi;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PispiDtos {

    private PispiDtos() {
    }

    public record TransferRequest(
            String endToEndId,
            String debtorAlias,
            String creditorAlias,
            Long amount,
            String currency,
            String description) {
    }

    public record TransferResponse(
            UUID id,
            String endToEndId,
            String status,
            String debtorAlias,
            String creditorAlias,
            long amount,
            String currency,
            Instant createdAt) {
    }

    public record InboundRequest(
            String endToEndId,
            String debtorAlias,
            String creditorAlias,
            Long amount,
            String currency,
            String description) {
    }

    public record SettlementLine(
            String endToEndId,
            String direction,
            long amount,
            String status) {
    }

    public record SettlementReport(
            String date,
            long netAmount,
            List<SettlementLine> lines) {
    }

    public record CallbackBody(
            UUID id,
            String endToEndId,
            String status,
            String debtorAlias,
            String creditorAlias,
            long amount,
            String currency) {
    }

    public record ErrorBody(String error, String message) {
    }
}
