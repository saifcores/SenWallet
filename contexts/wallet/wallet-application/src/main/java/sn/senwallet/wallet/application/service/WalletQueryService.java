package sn.senwallet.wallet.application.service;

import java.util.List;
import sn.senwallet.shared.id.WalletId;
import sn.senwallet.wallet.application.exception.WalletProblem;
import sn.senwallet.wallet.application.model.HistoryEntry;
import sn.senwallet.wallet.application.port.in.WalletQuery;
import sn.senwallet.wallet.application.port.out.LedgerGateway;
import sn.senwallet.wallet.application.port.out.WalletHistoryStore;
import sn.senwallet.wallet.domain.repository.WalletRepository;

public final class WalletQueryService implements WalletQuery {

    private final WalletRepository wallets;
    private final LedgerGateway ledger;
    private final WalletHistoryStore history;

    public WalletQueryService(WalletRepository wallets, LedgerGateway ledger, WalletHistoryStore history) {
        this.wallets = wallets;
        this.ledger = ledger;
        this.history = history;
    }

    @Override
    public WalletDetails wallet(WalletId id) {
        var wallet = wallets.findById(id)
                .orElseThrow(() -> WalletProblem.notFound("wallet " + id + " does not exist"));
        var platform = ledger.ensurePlatform();
        return new WalletDetails(wallet, platform.ledgerId(), ledger.balances(wallet.ledgerAccountId()));
    }

    @Override
    public List<HistoryEntry> history(WalletId id) {
        if (wallets.findById(id).isEmpty()) {
            throw WalletProblem.notFound("wallet " + id + " does not exist");
        }
        return history.findByWallet(id);
    }
}
