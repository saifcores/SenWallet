package sn.senwallet.wallet.application.port.in;

import java.util.UUID;
import sn.senwallet.shared.id.WalletId;
import sn.senwallet.wallet.application.model.SendResult;

public interface SendTransferUseCase {

    SendResult send(UUID idempotencyKey, WalletId fromWalletId, String toPhone, long amount, String currency);
}
