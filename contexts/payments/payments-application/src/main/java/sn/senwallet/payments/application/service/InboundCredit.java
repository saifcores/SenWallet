package sn.senwallet.payments.application.service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import sn.senwallet.payments.application.exception.HoldRejected;
import sn.senwallet.payments.application.port.out.LedgerPort;
import sn.senwallet.payments.application.port.out.LedgerPort.Condition;
import sn.senwallet.payments.application.port.out.LedgerPort.Hold;
import sn.senwallet.payments.application.port.out.LedgerPort.Line;
import sn.senwallet.payments.application.port.out.LedgerPort.RailAccounts;
import sn.senwallet.payments.application.port.out.PaymentOrderRepository;
import sn.senwallet.payments.application.port.out.WalletDirectory;
import sn.senwallet.payments.application.port.out.WalletDirectory.ProjectedWallet;
import sn.senwallet.payments.domain.model.PaymentOrder;

/** Posts a PI-SPI credit directly. An unknown alias, or a cap breach, goes to suspense. */
public final class InboundCredit {

    private final PaymentOrderRepository orders;
    private final WalletDirectory wallets;
    private final LedgerPort ledger;
    private final Clock clock;

    public InboundCredit(
            PaymentOrderRepository orders,
            WalletDirectory wallets,
            LedgerPort ledger,
            Clock clock) {
        this.orders = orders;
        this.wallets = wallets;
        this.ledger = ledger;
        this.clock = clock;
    }

    public void credit(String endToEndId, String creditorAlias, long amount) {
        if (endToEndId == null || endToEndId.isBlank() || amount <= 0) {
            return;
        }
        if (orders.findByProviderRef(endToEndId).isPresent()) {
            return;
        }
        var wallet = creditorAlias == null ? null : wallets.findByAlias(creditorAlias).orElse(null);
        var accounts = accounts(wallet);
        UUID walletId = null;
        UUID txnId;
        if (wallet == null) {
            txnId = postSuspense(accounts, endToEndId, amount);
        } else {
            try {
                txnId = ledger.postNow(toWallet(accounts, wallet, endToEndId, amount));
                walletId = wallet.walletId();
            } catch (HoldRejected rejected) {
                txnId = postSuspense(accounts, endToEndId, amount);
            }
        }
        orders.insert(PaymentOrder.inbound(
                UUID.randomUUID(), walletId, amount, endToEndId, txnId, creditorAlias, clock.instant()));
    }

    private RailAccounts accounts(ProjectedWallet wallet) {
        if (wallet != null) {
            return ledger.ensureRails(wallet.ledgerId(), wallet.feeAccountId());
        }
        return ledger.currentRails()
                .orElseThrow(() -> new IllegalStateException("rail accounts are not open"));
    }

    private UUID postSuspense(RailAccounts accounts, String endToEndId, long amount) {
        var lines = List.of(
                new Line(accounts.compensationId(), "debit", amount),
                new Line(accounts.suspenseId(), "credit", amount));
        return ledger.postNow(new Hold(accounts.ledgerId(), "pispi-in:" + endToEndId, "pispi-in", lines, List.of()));
    }

    private static Hold toWallet(RailAccounts accounts, ProjectedWallet wallet, String endToEndId, long amount) {
        var lines = List.of(
                new Line(accounts.compensationId(), "debit", amount),
                new Line(wallet.ledgerAccountId(), "credit", amount));
        var conditions = List.of(new Condition(wallet.ledgerAccountId(), "pending", "lte", wallet.holdingCap()));
        return new Hold(accounts.ledgerId(), "pispi-in:" + endToEndId, "pispi-in", lines, conditions);
    }
}
