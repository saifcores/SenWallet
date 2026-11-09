package sn.senwallet.wallet.domain.repository;

import java.util.Optional;
import java.util.UUID;
import sn.senwallet.shared.id.CustomerId;
import sn.senwallet.shared.id.WalletId;
import sn.senwallet.wallet.domain.model.Phone;
import sn.senwallet.wallet.domain.model.Wallet;

public interface WalletRepository {

    void insert(Wallet wallet);

    Optional<Wallet> findById(WalletId id);

    Optional<Wallet> findByAlias(Phone alias);

    Optional<Wallet> findByCustomer(CustomerId customerId);

    Optional<Wallet> findByLedgerAccount(UUID ledgerAccountId);
}
