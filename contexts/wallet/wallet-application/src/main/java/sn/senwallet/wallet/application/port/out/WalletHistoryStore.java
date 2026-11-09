package sn.senwallet.wallet.application.port.out;

import java.util.List;
import sn.senwallet.shared.id.WalletId;
import sn.senwallet.wallet.application.model.HistoryEntry;

public interface WalletHistoryStore {

    void append(HistoryEntry entry);

    List<HistoryEntry> findByWallet(WalletId walletId);
}
