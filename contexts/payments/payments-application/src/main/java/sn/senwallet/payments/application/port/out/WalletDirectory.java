package sn.senwallet.payments.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface WalletDirectory {

    Optional<ProjectedWallet> find(UUID walletId);

    Optional<ProjectedWallet> findByAlias(String alias);

    void upsert(ProjectedWallet wallet);

    record ProjectedWallet(
            UUID walletId,
            UUID customerId,
            UUID ledgerId,
            UUID ledgerAccountId,
            String alias,
            String phone,
            String tier,
            long holdingCap,
            UUID feeAccountId) {
    }
}
