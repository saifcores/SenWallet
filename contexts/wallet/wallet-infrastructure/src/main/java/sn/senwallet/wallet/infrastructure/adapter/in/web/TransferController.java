package sn.senwallet.wallet.infrastructure.adapter.in.web;

import java.net.URI;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import sn.senwallet.shared.id.WalletId;
import sn.senwallet.wallet.application.exception.WalletProblem;
import sn.senwallet.wallet.application.model.SendResult;
import sn.senwallet.wallet.application.port.in.SendTransferUseCase;
import sn.senwallet.wallet.domain.model.Transfer;
import sn.senwallet.wallet.infrastructure.adapter.in.web.WalletDtos.TransferRequest;
import sn.senwallet.wallet.infrastructure.adapter.in.web.WalletDtos.TransferResponse;

@RestController
public class TransferController {

    private final SendTransferUseCase transfers;

    public TransferController(SendTransferUseCase transfers) {
        this.transfers = transfers;
    }

    @PostMapping("/transfers")
    public ResponseEntity<?> create(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) TransferRequest body) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw WalletProblem.invalid("Idempotency-Key", "Idempotency-Key is required");
        }
        UUID key;
        try {
            key = UUID.fromString(idempotencyKey);
        } catch (IllegalArgumentException e) {
            throw WalletProblem.invalid("Idempotency-Key", "Idempotency-Key must be a UUID");
        }
        if (body == null) {
            throw WalletProblem.invalid("body", "body is required");
        }
        if (body.fromWalletId() == null) {
            throw WalletProblem.invalid("from_wallet_id", "from_wallet_id is required");
        }
        if (body.amount() == null) {
            throw WalletProblem.invalid("amount", "amount is required");
        }
        var result = transfers.send(key, new WalletId(body.fromWalletId()), body.toPhone(), body.amount(),
                body.currency());
        return switch (result) {
            case SendResult.Accepted accepted -> ResponseEntity
                    .created(URI.create("/transfers/" + accepted.transfer().id()))
                    .body(response(accepted.transfer()));
            case SendResult.Refused refused -> ResponseEntity.status(refused.status())
                    .body(problem(refused));
        };
    }

    private static TransferResponse response(Transfer transfer) {
        return new TransferResponse(
                transfer.id().value(),
                transfer.fromWalletId().value(),
                transfer.toWalletId().value(),
                transfer.amount().toMinorUnits(),
                transfer.fee().toMinorUnits(),
                transfer.amount().currency().name(),
                transfer.status().name().toLowerCase(Locale.ROOT),
                transfer.ledgerTransactionId());
    }

    private static ProblemDetail problem(SendResult.Refused refused) {
        var body = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(refused.status()), refused.detail());
        body.setType(URI.create(WalletProblemHandler.TYPE_PREFIX + refused.code()));
        body.setTitle(refused.code().replace('_', ' '));
        body.setProperty("code", refused.code());
        body.setProperty("transfer_id", refused.transfer().id().value());
        return body;
    }
}
