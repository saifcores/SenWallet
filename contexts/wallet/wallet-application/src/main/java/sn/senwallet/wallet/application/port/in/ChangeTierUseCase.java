package sn.senwallet.wallet.application.port.in;

import sn.senwallet.shared.id.CustomerId;
import sn.senwallet.wallet.domain.model.Customer;
import sn.senwallet.wallet.domain.model.KycTier;

public interface ChangeTierUseCase {

    Customer changeTier(CustomerId customerId, KycTier tier);
}
