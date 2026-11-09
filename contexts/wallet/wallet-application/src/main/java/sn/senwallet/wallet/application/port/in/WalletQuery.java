package sn.senwallet.wallet.application.port.in;

import java.util.List;
import sn.senwallet.shared.id.LedgerId;
import sn.senwallet.shared.id.WalletId;
import sn.senwallet.wallet.application.model.HistoryEntry;
import sn.senwallet.wallet.application.port.out.LedgerGateway.Balances;
import sn.senwallet.wallet.domain.model.Wallet;

public interface WalletQuery {

    WalletDetails wallet(WalletId id);

    List<HistoryEntry> history(WalletId id);

    record WalletDetails(Wallet wallet, LedgerId ledgerId, Balances balances) {
    }
}
