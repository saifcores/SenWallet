package sn.senwallet.ledger.domain.repository;

import java.util.Optional;
import sn.senwallet.ledger.domain.model.ledger.Ledger;
import sn.senwallet.shared.id.LedgerId;

public interface LedgerRepository {

    void insert(Ledger ledger);

    Optional<Ledger> findById(LedgerId id);
}
