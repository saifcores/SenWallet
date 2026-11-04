package sn.senwallet.ledger.infrastructure.adapter.in.web;

import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.senwallet.ledger.application.port.in.LedgerLookupQuery;
import sn.senwallet.ledger.application.port.in.PostTransactionUseCase;
import sn.senwallet.ledger.application.port.in.PostingResult;
import sn.senwallet.ledger.application.port.in.ReverseTransactionUseCase;
import sn.senwallet.ledger.application.port.in.TransitionTransactionUseCase;
import sn.senwallet.ledger.infrastructure.adapter.in.web.LedgerDtos.PostTransactionRequest;
import sn.senwallet.ledger.infrastructure.adapter.in.web.LedgerDtos.ReversalRequest;
import sn.senwallet.ledger.infrastructure.adapter.in.web.LedgerDtos.TransactionResponse;
import sn.senwallet.ledger.infrastructure.adapter.in.web.LedgerDtos.TransitionRequest;
import sn.senwallet.shared.id.TransactionId;

/**
 * Postings answer 201 when this call wrote the transaction and 200 when the
 * same request had already been posted (a replay).
 */
@RestController
@RequestMapping("/ledger_transactions")
public class LedgerTransactionController {

    private final PostTransactionUseCase post;
    private final TransitionTransactionUseCase transition;
    private final ReverseTransactionUseCase reverse;
    private final LedgerLookupQuery lookup;

    public LedgerTransactionController(
            PostTransactionUseCase post,
            TransitionTransactionUseCase transition,
            ReverseTransactionUseCase reverse,
            LedgerLookupQuery lookup) {
        this.post = post;
        this.transition = transition;
        this.reverse = reverse;
        this.lookup = lookup;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> post(@RequestBody(required = false) PostTransactionRequest body) {
        return posting(post.post(LedgerWebMapper.toCommand(body)));
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@PathVariable UUID id) {
        return LedgerWebMapper.toResponse(lookup.transaction(new TransactionId(id)), null);
    }

    /** {@code pending -> posted | archived}; asking for the current status again is a 200 no-op. */
    @PatchMapping("/{id}")
    public TransactionResponse transition(
            @PathVariable UUID id, @RequestBody(required = false) TransitionRequest body) {
        var result = transition.transition(LedgerWebMapper.toCommand(id, body));
        return LedgerWebMapper.toResponse(result.transaction(), result.balances());
    }

    @PostMapping("/{id}/reversal")
    public ResponseEntity<TransactionResponse> reverse(
            @PathVariable UUID id, @RequestBody(required = false) ReversalRequest body) {
        return posting(reverse.reverse(LedgerWebMapper.toCommand(id, body)));
    }

    private static ResponseEntity<TransactionResponse> posting(PostingResult result) {
        var response = LedgerWebMapper.toResponse(result.transaction(), result.balances());
        return switch (result) {
            case PostingResult.Created created -> ResponseEntity
                    .created(URI.create("/ledger_transactions/" + created.transaction().id()))
                    .body(response);
            case PostingResult.Replayed replayed -> ResponseEntity.status(HttpStatus.OK).body(response);
        };
    }
}
