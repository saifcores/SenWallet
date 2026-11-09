package sn.senwallet.wallet.domain.repository;

import java.util.Optional;
import sn.senwallet.wallet.domain.model.Transfer;
import sn.senwallet.wallet.domain.model.TransferId;

public interface TransferRepository {

    void insert(Transfer transfer);

    Optional<Transfer> findById(TransferId id);
}
