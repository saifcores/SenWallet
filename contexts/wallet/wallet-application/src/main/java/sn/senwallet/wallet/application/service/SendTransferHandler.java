package sn.senwallet.wallet.application.service;

import java.time.Clock;
import java.util.UUID;
import sn.senwallet.shared.id.WalletId;
import sn.senwallet.shared.money.Money;
import sn.senwallet.wallet.application.exception.WalletProblem;
import sn.senwallet.wallet.application.model.SendResult;
import sn.senwallet.wallet.application.port.in.SendTransferUseCase;
import sn.senwallet.wallet.application.port.out.LedgerGateway;
import sn.senwallet.wallet.application.port.out.PricingCatalog;
import sn.senwallet.wallet.application.port.out.TransferIdempotency;
import sn.senwallet.wallet.domain.model.Phone;
import sn.senwallet.wallet.domain.model.P2pPosting;
import sn.senwallet.wallet.domain.model.Transfer;
import sn.senwallet.wallet.domain.model.Wallet;
import sn.senwallet.wallet.domain.repository.CustomerRepository;
import sn.senwallet.wallet.domain.repository.WalletRepository;

public final class SendTransferHandler implements SendTransferUseCase {

    private final TransferIdempotency idempotency;
    private final WalletRepository wallets;
    private final CustomerRepository customers;
    private final PricingCatalog pricing;
    private final LedgerGateway ledger;
    private final Clock clock;

    public SendTransferHandler(
            TransferIdempotency idempotency,
            WalletRepository wallets,
            CustomerRepository customers,
            PricingCatalog pricing,
            LedgerGateway ledger,
            Clock clock) {
        this.idempotency = idempotency;
        this.wallets = wallets;
        this.customers = customers;
        this.pricing = pricing;
        this.ledger = ledger;
        this.clock = clock;
    }

    @Override
    public SendResult send(UUID idempotencyKey, WalletId fromWalletId, String toPhone, long amount, String currency) {
        if (idempotencyKey == null) {
            throw WalletProblem.invalid("Idempotency-Key", "Idempotency-Key is required");
        }
        if (fromWalletId == null) {
            throw WalletProblem.invalid("from_wallet_id", "from_wallet_id is required");
        }
        if (amount <= 0) {
            throw WalletProblem.invalid("amount", "amount must be positive");
        }
        if (!"XOF".equals(currency)) {
            throw WalletProblem.invalid("currency", "currency must be XOF");
        }
        var recipientPhone = RegisterCustomerHandler.phone(toPhone);
        var hash = TransferRequestHasher.hash(fromWalletId, recipientPhone.value(), amount, currency);
        var begin = idempotency.begin(idempotencyKey, hash);
        if (begin.finished()) {
            return begin.previous();
        }

        var from = wallets.findById(fromWalletId)
                .orElseThrow(() -> WalletProblem.notFound("wallet " + fromWalletId + " does not exist"));
        var to = wallets.findByAlias(recipientPhone)
                .orElseThrow(() -> WalletProblem.notFound("no wallet for " + recipientPhone.value()));
        if (from.id().equals(to.id())) {
            throw new WalletProblem(422, "invalid_transfer", "sender and recipient are the same wallet");
        }
        var money = Money.xof(amount);
        var fee = pricing.p2pFee(clock.instant()).feeFor(money);
        return post(idempotencyKey, begin.transferId(), from, to, money, fee);
    }

    private SendResult post(
            UUID idempotencyKey,
            sn.senwallet.wallet.domain.model.TransferId transferId,
            Wallet from,
            Wallet to,
            Money amount,
            Money fee) {
        var recipient = customers.findById(to.customerId())
                .orElseThrow(() -> WalletProblem.notFound("customer " + to.customerId() + " does not exist"));
        var cap = pricing.limit(recipient.tier(), clock.instant());
        var platform = ledger.ensurePlatform();
        var posting = P2pPosting.of(
                transferId,
                from.ledgerAccountId().value(),
                to.ledgerAccountId().value(),
                platform.feeAccountId().value(),
                amount,
                fee,
                cap.holdingCap());
        var posted = ledger.post(posting);
        SendResult result = posted.accepted()
                ? new SendResult.Accepted(Transfer.posted(
                        transferId, from.id(), to.id(), amount, fee, posted.transactionId()))
                : new SendResult.Refused(
                        Transfer.rejected(transferId, from.id(), to.id(), amount, fee),
                        posted.status(), posted.code(), posted.detail());
        idempotency.finish(idempotencyKey, result);
        return result;
    }
}
