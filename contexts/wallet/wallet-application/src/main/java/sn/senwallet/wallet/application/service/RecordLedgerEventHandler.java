package sn.senwallet.wallet.application.service;

import java.util.ArrayList;
import sn.senwallet.wallet.application.model.HistoryEntry;
import sn.senwallet.wallet.application.model.LedgerFact;
import sn.senwallet.wallet.application.port.out.LedgerGateway;
import sn.senwallet.wallet.application.port.out.LedgerGateway.LedgerLine;
import sn.senwallet.wallet.application.port.out.SmsPort;
import sn.senwallet.wallet.application.port.out.WalletHistoryStore;
import sn.senwallet.wallet.domain.model.Phone;
import sn.senwallet.wallet.domain.repository.WalletRepository;

/**
 * Turns one ledger notification into a history row and a simulated SMS for
 * each wallet the transaction touched. The caller deduplicates on event id.
 */
public final class RecordLedgerEventHandler {

    private final LedgerGateway ledger;
    private final WalletRepository wallets;
    private final WalletHistoryStore history;
    private final SmsPort sms;

    public RecordLedgerEventHandler(
            LedgerGateway ledger,
            WalletRepository wallets,
            WalletHistoryStore history,
            SmsPort sms) {
        this.ledger = ledger;
        this.wallets = wallets;
        this.history = history;
        this.sms = sms;
    }

    public void record(LedgerFact fact) {
        var transaction = ledger.transaction(fact.transactionId());
        var touched = new ArrayList<Touched>();
        for (var line : transaction.entries()) {
            var wallet = wallets.findByLedgerAccount(line.accountId());
            if (wallet.isPresent()) {
                touched.add(new Touched(wallet.get().id(), wallet.get().alias(), line));
            }
        }
        for (var line : touched) {
            history.append(new HistoryEntry(
                    fact.eventId(),
                    fact.eventType(),
                    line.walletId(),
                    fact.transactionId(),
                    fact.externalId(),
                    fact.ledgerId(),
                    line.entry().direction(),
                    line.entry().amount(),
                    fact.occurredAt()));
            sms.send(line.phone(), text(line, touched, fact.externalId()));
        }
    }

    private static String text(Touched line, java.util.List<Touched> touched, String externalId) {
        long principal = line.entry().amount();
        if ("debit".equals(line.entry().direction())) {
            principal = touched.stream()
                    .map(Touched::entry)
                    .filter(entry -> "credit".equals(entry.direction()))
                    .mapToLong(LedgerLine::amount)
                    .findFirst()
                    .orElse(principal);
            return "Vous avez envoyé " + principal + " XOF (" + externalId + ")";
        }
        return "Vous avez reçu " + principal + " XOF (" + externalId + ")";
    }

    private record Touched(
            sn.senwallet.shared.id.WalletId walletId,
            Phone phone,
            LedgerLine entry) {
    }
}
