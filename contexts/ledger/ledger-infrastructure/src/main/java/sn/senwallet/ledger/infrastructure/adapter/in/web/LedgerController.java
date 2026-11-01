package sn.senwallet.ledger.infrastructure.adapter.in.web;

import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.senwallet.ledger.application.port.in.CreateLedgerUseCase;
import sn.senwallet.ledger.application.port.in.LedgerLookupQuery;
import sn.senwallet.ledger.infrastructure.adapter.in.web.LedgerDtos.CreateLedgerRequest;
import sn.senwallet.ledger.infrastructure.adapter.in.web.LedgerDtos.LedgerResponse;
import sn.senwallet.shared.id.LedgerId;

@RestController
@RequestMapping("/ledgers")
public class LedgerController {

    private final CreateLedgerUseCase createLedger;
    private final LedgerLookupQuery lookup;

    public LedgerController(CreateLedgerUseCase createLedger, LedgerLookupQuery lookup) {
        this.createLedger = createLedger;
        this.lookup = lookup;
    }

    @PostMapping
    public ResponseEntity<LedgerResponse> create(@RequestBody(required = false) CreateLedgerRequest body) {
        var ledger = createLedger.create(LedgerWebMapper.toCommand(body));
        return ResponseEntity.created(URI.create("/ledgers/" + ledger.id()))
                .body(LedgerWebMapper.toResponse(ledger));
    }

    @GetMapping("/{id}")
    public LedgerResponse get(@PathVariable UUID id) {
        return LedgerWebMapper.toResponse(lookup.ledger(new LedgerId(id)));
    }
}
