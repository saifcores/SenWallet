package sn.senwallet.payments.infrastructure.adapter.in.web;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import sn.senwallet.payments.application.exception.PaymentProblem;
import sn.senwallet.payments.application.model.PaymentOutcome;
import sn.senwallet.payments.application.model.StartCommand;
import sn.senwallet.payments.application.port.in.StartPaymentUseCase;
import sn.senwallet.payments.application.service.PaymentBodies;
import sn.senwallet.payments.domain.model.PaymentOrder;

@RestController
public class PaymentController {

    private final StartPaymentUseCase payments;

    public PaymentController(StartPaymentUseCase payments) {
        this.payments = payments;
    }

    @PostMapping("/topups")
    public ResponseEntity<String> topup(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) PaymentRequest body) {
        return start(idempotencyKey, body, PaymentOrder.Type.TOPUP, true);
    }

    @PostMapping("/withdrawals")
    public ResponseEntity<String> withdraw(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) PaymentRequest body) {
        return start(idempotencyKey, body, PaymentOrder.Type.CASHOUT, true);
    }

    @PostMapping("/pispi-transfers")
    public ResponseEntity<String> pispi(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) PaymentRequest body) {
        return start(idempotencyKey, body, PaymentOrder.Type.PISPI_OUT, false);
    }

    @GetMapping("/payment-orders/{id}")
    public ResponseEntity<String> get(@PathVariable UUID id) {
        return PaymentProblemHandler.json(200, PaymentBodies.order(payments.get(id)));
    }

    private ResponseEntity<String> start(
            String idempotencyKey,
            PaymentRequest body,
            PaymentOrder.Type type,
            boolean mobileMoney) {
        if (body == null) {
            throw PaymentProblem.invalid("body", "body is required");
        }
        var provider = mobileMoney ? provider(body.provider()) : PaymentOrder.Provider.PISPI;
        var outcome = payments.start(new StartCommand(
                key(idempotencyKey),
                body.walletId(),
                type,
                provider,
                body.amount() == null ? 0 : body.amount(),
                body.currency(),
                body.phone(),
                body.creditorAlias()));
        if (outcome.replayBody() != null) {
            return PaymentProblemHandler.json(outcome.httpStatus(), outcome.replayBody());
        }
        return PaymentProblemHandler.json(outcome.httpStatus(), render(outcome));
    }

    private static String render(PaymentOutcome outcome) {
        if (outcome.code() != null) {
            return PaymentBodies.problem(outcome.code(), outcome.detail(), outcome.order());
        }
        return PaymentBodies.order(outcome.order());
    }

    private static UUID key(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw PaymentProblem.invalid("Idempotency-Key", "Idempotency-Key is required");
        }
        try {
            return UUID.fromString(idempotencyKey);
        } catch (IllegalArgumentException e) {
            throw PaymentProblem.invalid("Idempotency-Key", "Idempotency-Key must be a UUID");
        }
    }

    private static PaymentOrder.Provider provider(String raw) {
        if (raw == null || raw.isBlank()) {
            throw PaymentProblem.invalid("provider", "provider is required");
        }
        try {
            return PaymentOrder.Provider.valueOf(raw.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw PaymentProblem.invalid("provider", "provider must be wave or orange");
        }
    }

    public record PaymentRequest(
            UUID walletId,
            String provider,
            Long amount,
            String currency,
            String phone,
            String creditorAlias) {
    }
}
