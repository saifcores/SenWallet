package sn.senwallet.wallet.infrastructure.adapter.in.web;

import java.util.Locale;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.senwallet.shared.id.WalletId;
import sn.senwallet.wallet.application.port.in.WalletQuery;
import sn.senwallet.wallet.domain.repository.CustomerRepository;
import sn.senwallet.wallet.infrastructure.adapter.in.web.WalletDtos.BalanceResponse;
import sn.senwallet.wallet.infrastructure.adapter.in.web.WalletDtos.HistoryItem;
import sn.senwallet.wallet.infrastructure.adapter.in.web.WalletDtos.HistoryResponse;
import sn.senwallet.wallet.infrastructure.adapter.in.web.WalletDtos.WalletResponse;

@RestController
@RequestMapping("/wallets")
public class WalletController {

    private final WalletQuery wallets;
    private final CustomerRepository customers;

    public WalletController(WalletQuery wallets, CustomerRepository customers) {
        this.wallets = wallets;
        this.customers = customers;
    }

    @GetMapping("/{id}")
    public WalletResponse get(@PathVariable UUID id) {
        var details = wallets.wallet(new WalletId(id));
        var wallet = details.wallet();
        var customer = customers.findById(wallet.customerId()).orElseThrow();
        var balances = details.balances();
        return new WalletResponse(
                wallet.id().value(),
                wallet.customerId().value(),
                details.ledgerId().value(),
                wallet.ledgerAccountId().value(),
                wallet.status().name().toLowerCase(Locale.ROOT),
                wallet.alias().value(),
                customer.tier().name().toLowerCase(Locale.ROOT),
                new BalanceResponse(balances.posted(), balances.pending(), balances.available()));
    }

    @GetMapping("/{id}/history")
    public HistoryResponse history(@PathVariable UUID id) {
        var entries = wallets.history(new WalletId(id)).stream()
                .map(entry -> new HistoryItem(
                        entry.eventId(),
                        entry.eventType(),
                        entry.transactionId(),
                        entry.externalId(),
                        entry.direction(),
                        entry.amount(),
                        entry.occurredAt()))
                .toList();
        return new HistoryResponse(entries);
    }
}
