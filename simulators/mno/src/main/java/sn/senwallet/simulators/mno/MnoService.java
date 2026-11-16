package sn.senwallet.simulators.mno;

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
import sn.senwallet.simulators.mno.MnoStore.StoredTx;
import tools.jackson.databind.json.JsonMapper;

@Service
public class MnoService {

    private final MnoStore store;
    private final FaultQueue faultQueue;
    private final CallbackDispatcher callbacks;
    private final JsonMapper jsonMapper;
    private final String provider;
    private final String callbackPath;

    public MnoService(
            MnoStore store,
            FaultQueue faultQueue,
            CallbackDispatcher callbacks,
            JsonMapper jsonMapper,
            @Value("${simulator.provider:wave}") String provider,
            @Value("${simulator.callback-path:/webhooks/mno}") String callbackPath) {
        this.store = store;
        this.faultQueue = faultQueue;
        this.callbacks = callbacks;
        this.jsonMapper = jsonMapper;
        this.provider = provider;
        this.callbackPath = callbackPath;
    }

    public MnoDtos.TransactionResponse collect(MnoDtos.MoneyRequest request) {
        return accept("collections", "collection", request, false);
    }

    public MnoDtos.TransactionResponse disburse(MnoDtos.MoneyRequest request) {
        return accept("disbursements", "disbursement", request, true);
    }

    public MnoDtos.TransactionResponse get(UUID id) {
        return store.find(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public MnoDtos.FloatResponse floatBalance() {
        return new MnoDtos.FloatResponse(provider, store.floatAvailable());
    }

    public MnoDtos.FloatResponse setFloat(MnoDtos.SetFloatRequest request) {
        if (request.available() == null || request.available() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "available required");
        }
        store.setFloat(request.available());
        return floatBalance();
    }

    public MnoDtos.SettlementFile settlement(LocalDate date) {
        List<MnoDtos.SettlementLine> lines = new ArrayList<>();
        for (StoredTx tx : store.onDate(date)) {
            lines.add(new MnoDtos.SettlementLine(
                    tx.externalId(),
                    tx.type(),
                    tx.amount(),
                    tx.status()));
        }
        return new MnoDtos.SettlementFile(date.toString(), provider, lines);
    }

    private MnoDtos.TransactionResponse accept(
            String operation,
            String type,
            MnoDtos.MoneyRequest request,
            boolean debitFloat) {
        requireAmount(request.amount());
        FaultEffect effect = faultQueue.consume(operation);
        FaultSupport.applyLatency(effect);

        if (effect instanceof FaultEffect.Reject) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    operation + " rejected by fault");
        }
        if (effect instanceof FaultEffect.FloatExhausted) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "float exhausted");
        }

        if (debitFloat && !store.tryDebitFloat(request.amount())) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "float exhausted");
        }
        if (!debitFloat) {
            store.creditFloat(request.amount());
        }

        UUID id = UUID.randomUUID();
        String externalId = blankToGenerated(request.externalId());
        String currency = blankTo(request.currency(), "XOF");

        if (effect instanceof FaultEffect.TimeoutThenSuccess timeout) {
            StoredTx pending = store.save(new StoredTx(
                    id,
                    externalId,
                    type,
                    "pending",
                    request.phone(),
                    request.amount(),
                    currency,
                    provider,
                    Instant.now()));
            finalizeAfterDelay(pending.id(), "succeeded", timeout.delayMs());
            return toResponse(pending);
        }
        if (effect instanceof FaultEffect.TimeoutThenFailure timeout) {
            StoredTx pending = store.save(new StoredTx(
                    id,
                    externalId,
                    type,
                    "pending",
                    request.phone(),
                    request.amount(),
                    currency,
                    provider,
                    Instant.now()));
            if (debitFloat) {
                store.creditFloat(request.amount());
            }
            finalizeAfterDelay(pending.id(), "failed", timeout.delayMs());
            return toResponse(pending);
        }

        StoredTx tx = store.save(new StoredTx(
                id,
                externalId,
                type,
                "succeeded",
                request.phone(),
                request.amount(),
                currency,
                provider,
                Instant.now()));

        if (effect instanceof FaultEffect.DuplicateCallback duplicate) {
            scheduleCallback(tx, "succeeded", duplicate.delayMs(), true, false);
        } else if (effect instanceof FaultEffect.OutOfOrderCallback outOfOrder) {
            scheduleCallback(tx, "succeeded", outOfOrder.delayMs(), false, true);
        } else {
            scheduleCallback(tx, "succeeded", 0L, false, false);
        }
        return toResponse(tx);
    }

    /**
     * Flips stored status after the delay, then posts the signed callback so
     * status polling and webhooks agree (needed by M7 enquêtes).
     */
    private void finalizeAfterDelay(UUID id, String status, long delayMs) {
        callbacks.afterDelay(delayMs, () -> store.updateStatus(id, status).ifPresent(tx ->
                callbacks.dispatchNow(callbackPath, toJson(callback(tx, status)))));
    }

    private void scheduleCallback(
            StoredTx tx,
            String status,
            long delayMs,
            boolean duplicate,
            boolean outOfOrder) {
        String success = toJson(callback(tx, status));
        if (outOfOrder) {
            callbacks.dispatchAsync(callbackPath, toJson(callback(tx, "failed")), 0L);
            callbacks.dispatchAsync(callbackPath, success, Math.max(delayMs, 20L));
            return;
        }
        callbacks.dispatchAsync(callbackPath, success, delayMs);
        if (duplicate) {
            callbacks.dispatchAsync(callbackPath, success, delayMs + 10L);
        }
    }

    private MnoDtos.CallbackBody callback(StoredTx tx, String status) {
        return new MnoDtos.CallbackBody(
                tx.id(),
                tx.externalId(),
                tx.type(),
                status,
                tx.phone(),
                tx.amount(),
                tx.currency(),
                tx.provider());
    }

    private MnoDtos.TransactionResponse toResponse(StoredTx tx) {
        return new MnoDtos.TransactionResponse(
                tx.id(),
                tx.externalId(),
                tx.type(),
                tx.status(),
                tx.phone(),
                tx.amount(),
                tx.currency(),
                tx.provider(),
                tx.createdAt());
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
