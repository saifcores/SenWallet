package sn.senwallet.wallet.application.port.out;

import java.util.UUID;
import sn.senwallet.shared.id.AccountId;
import sn.senwallet.shared.id.LedgerId;
import sn.senwallet.wallet.domain.model.P2pPosting;

/** The wallet's view of the ledger HTTP API. */
public interface LedgerGateway {

    PlatformAccounts ensurePlatform();

    AccountId openWalletAccount(LedgerId ledgerId, String name);

    PostResult post(P2pPosting posting);

    LedgerTransaction transaction(UUID transactionId);

    Balances balances(AccountId accountId);

    record PlatformAccounts(LedgerId ledgerId, AccountId feeAccountId) {
    }

    record Balances(long posted, long pending, long available) {
    }

    record LedgerLine(UUID accountId, String direction, long amount) {
    }

    record LedgerTransaction(UUID id, String externalId, java.util.List<LedgerLine> entries) {
    }

    record PostResult(boolean accepted, UUID transactionId, int status, String code, String detail) {

        public static PostResult posted(UUID transactionId) {
            return new PostResult(true, transactionId, 201, null, null);
        }

        public static PostResult rejected(int status, String code, String detail) {
            return new PostResult(false, null, status, code, detail);
        }
    }
}
