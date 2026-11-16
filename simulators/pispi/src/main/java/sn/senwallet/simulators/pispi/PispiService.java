package sn.senwallet.simulators.pispi;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import sn.senwallet.simulators.common.FaultSupport;
import sn.senwallet.simulators.common.callback.CallbackDispatcher;
import sn.senwallet.simulators.common.fault.FaultEffect;
import sn.senwallet.simulators.common.fault.FaultQueue;
import sn.senwallet.simulators.pispi.TransferStore.StoredTransfer;
import tools.jackson.databind.json.JsonMapper;

@Service
public class PispiService {

    private final TransferStore store;
    private final FaultQueue faultQueue;
    private final CallbackDispatcher callbacks;
    private final JsonMapper jsonMapper;
    private final String callbackPath;

    public PispiService(
            TransferStore store,
            FaultQueue faultQueue,
            CallbackDispatcher callbacks,
            JsonMapper jsonMapper,
            @Value("${simulator.callback-path:/webhooks/pispi}") String callbackPath) {
        this.store = store;
        this.faultQueue = faultQueue;
        this.callbacks = callbacks;
        this.jsonMapper = jsonMapper;
        this.callbackPath = callbackPath;
    }

    public PispiDtos.TransferResponse outbound(PispiDtos.TransferRequest request) {
        requireAmount(request.amount());
        FaultEffect effect = faultQueue.consume("transfers");
        FaultSupport.applyLatency(effect);

        if (effect instanceof FaultEffect.Reject
                || effect instanceof FaultEffect.FloatExhausted) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "transfer rejected by fault");
        }

        UUID id = UUID.randomUUID();
        String endToEnd = blankToGenerated(request.endToEndId());
        String currency = blankTo(request.currency(), "XOF");

        if (effect instanceof FaultEffect.TimeoutThenSuccess timeout) {
            StoredTransfer pending = store.save(new StoredTransfer(
                    id,
                    endToEnd,
                    "pending",
                    "outbound",
                    request.debtorAlias(),
                    request.creditorAlias(),
                    request.amount(),
                    currency,
                    Instant.now()));
            finalizeAfterDelay(pending.id(), "posted", timeout.delayMs());
            return toResponse(pending);
        }
        if (effect instanceof FaultEffect.TimeoutThenFailure timeout) {
            StoredTransfer pending = store.save(new StoredTransfer(
                    id,
                    endToEnd,
                    "pending",
                    "outbound",
                    request.debtorAlias(),
                    request.creditorAlias(),
                    request.amount(),
                    currency,
                    Instant.now()));
            finalizeAfterDelay(pending.id(), "failed", timeout.delayMs());
            return toResponse(pending);
        }

        String status = "posted";
        StoredTransfer transfer = store.save(new StoredTransfer(
                id,
                endToEnd,
                status,
                "outbound",
                request.debtorAlias(),
                request.creditorAlias(),
                request.amount(),
                currency,
                Instant.now()));

        if (effect instanceof FaultEffect.DuplicateCallback duplicate) {
            scheduleCallback(transfer, status, duplicate.delayMs(), true, false);
        } else if (effect instanceof FaultEffect.OutOfOrderCallback outOfOrder) {
            scheduleCallback(transfer, status, outOfOrder.delayMs(), false, true);
        } else {
            scheduleCallback(transfer, status, 0L, false, false);
        }
        return toResponse(transfer);
    }

    public PispiDtos.TransferResponse get(UUID id) {
        return store.find(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public PispiDtos.TransferResponse inbound(PispiDtos.InboundRequest request) {
        requireAmount(request.amount());
        UUID id = UUID.randomUUID();
        StoredTransfer transfer = store.save(new StoredTransfer(
                id,
                blankToGenerated(request.endToEndId()),
                "posted",
                "inbound",
                request.debtorAlias(),
                request.creditorAlias(),
                request.amount(),
                blankTo(request.currency(), "XOF"),
                Instant.now()));
        scheduleCallback(transfer, "posted", 0L, false, false);
        return toResponse(transfer);
    }

    public PispiDtos.SettlementReport settlement(LocalDate date) {
        List<PispiDtos.SettlementLine> lines = new ArrayList<>();
        long net = 0L;
        for (StoredTransfer transfer : store.onDate(date)) {
            long signed = "inbound".equals(transfer.direction())
                    ? transfer.amount()
                    : -transfer.amount();
            net += signed;
            lines.add(new PispiDtos.SettlementLine(
                    transfer.endToEndId(),
                    transfer.direction(),
                    transfer.amount(),
                    transfer.status()));
        }
        return new PispiDtos.SettlementReport(date.toString(), net, lines);
    }

    /**
     * Flips stored status after the delay, then posts the signed callback so
     * status polling and webhooks agree (needed by M7 enquêtes).
     */
    private void finalizeAfterDelay(UUID id, String status, long delayMs) {
        callbacks.afterDelay(delayMs, () -> store.updateStatus(id, status).ifPresent(transfer ->
                callbacks.dispatchNow(callbackPath, toJson(callbackBody(transfer, status)))));
    }

    private void scheduleCallback(
            StoredTransfer transfer,
            String status,
            long delayMs,
            boolean duplicate,
            boolean outOfOrder) {
        String body = toJson(callbackBody(transfer, status));
        if (outOfOrder) {
            callbacks.dispatchAsync(callbackPath, toJson(callbackBody(transfer, "failed")), 0L);
            callbacks.dispatchAsync(callbackPath, body, Math.max(delayMs, 20L));
            return;
        }
        callbacks.dispatchAsync(callbackPath, body, delayMs);
        if (duplicate) {
            callbacks.dispatchAsync(callbackPath, body, delayMs + 10L);
        }
    }

    private static PispiDtos.CallbackBody callbackBody(
            StoredTransfer transfer,
            String status) {
        return new PispiDtos.CallbackBody(
                transfer.id(),
                transfer.endToEndId(),
                status,
                transfer.debtorAlias(),
                transfer.creditorAlias(),
                transfer.amount(),
                transfer.currency());
    }

    private PispiDtos.TransferResponse toResponse(StoredTransfer transfer) {
        return new PispiDtos.TransferResponse(
                transfer.id(),
                transfer.endToEndId(),
                transfer.status(),
                transfer.debtorAlias(),
                transfer.creditorAlias(),
                transfer.amount(),
                transfer.currency(),
                transfer.createdAt());
    }

    private String toJson(Object value) {
        return jsonMapper.writeValueAsString(value);
    }

    private static void requireAmount(Long amount) {
        if (amount == null || amount <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "amount must be a positive integer");
        }
    }

    private static String blankToGenerated(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
