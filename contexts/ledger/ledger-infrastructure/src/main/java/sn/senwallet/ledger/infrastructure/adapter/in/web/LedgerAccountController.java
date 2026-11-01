package sn.senwallet.ledger.infrastructure.adapter.in.web;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.senwallet.ledger.application.port.in.BalanceQuery;
import sn.senwallet.ledger.application.port.in.LedgerLookupQuery;
import sn.senwallet.ledger.application.port.in.OpenAccountUseCase;
import sn.senwallet.ledger.infrastructure.adapter.in.web.LedgerDtos.AccountResponse;
import sn.senwallet.ledger.infrastructure.adapter.in.web.LedgerDtos.BalancesResponse;
import sn.senwallet.ledger.infrastructure.adapter.in.web.LedgerDtos.OpenAccountRequest;
import sn.senwallet.shared.id.AccountId;

@RestController
@RequestMapping("/ledger_accounts")
public class LedgerAccountController {

    private final OpenAccountUseCase openAccount;
    private final LedgerLookupQuery lookup;
    private final BalanceQuery balances;

    public LedgerAccountController(OpenAccountUseCase openAccount, LedgerLookupQuery lookup, BalanceQuery balances) {
        this.openAccount = openAccount;
        this.lookup = lookup;
        this.balances = balances;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> open(@RequestBody(required = false) OpenAccountRequest body) {
        var account = openAccount.open(LedgerWebMapper.toCommand(body));
        return ResponseEntity.created(URI.create("/ledger_accounts/" + account.id()))
                .body(LedgerWebMapper.toResponse(account));
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable UUID id) {
        return LedgerWebMapper.toResponse(lookup.account(new AccountId(id)));
    }

    /** Current balances, or balances as of a past instant when {@code as_of} is given. */
    @GetMapping("/{id}/balances")
    public BalancesResponse balances(
            @PathVariable UUID id, @RequestParam(name = "as_of", required = false) Instant asOf) {
        var accountId = new AccountId(id);
        return asOf == null
                ? LedgerWebMapper.toResponse(balances.current(accountId), null)
                : LedgerWebMapper.toResponse(balances.asOf(accountId, asOf), asOf);
    }
}
