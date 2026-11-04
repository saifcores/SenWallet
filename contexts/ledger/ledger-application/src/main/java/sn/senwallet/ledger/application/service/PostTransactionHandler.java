package sn.senwallet.ledger.application.service;

import org.springframework.transaction.annotation.Transactional;
import sn.senwallet.ledger.application.command.PostTransactionCommand;
import sn.senwallet.ledger.application.exception.LedgerNotFoundException;
import sn.senwallet.ledger.application.port.in.PostTransactionUseCase;
import sn.senwallet.ledger.application.port.in.PostingResult;
import sn.senwallet.ledger.application.port.out.ClockPort;
import sn.senwallet.ledger.domain.model.transaction.LedgerTransaction;
import sn.senwallet.ledger.domain.repository.LedgerAccountRepository;
import sn.senwallet.ledger.domain.repository.LedgerRepository;
import sn.senwallet.ledger.domain.repository.LedgerTransactionRepository;

@Transactional
public class PostTransactionHandler implements PostTransactionUseCase {

    private final LedgerRepository ledgers;
    private final TransactionWriter writer;
    private final ClockPort clock;

    public PostTransactionHandler(
            LedgerRepository ledgers,
            LedgerTransactionRepository transactions,
            LedgerAccountRepository accounts,
            ClockPort clock) {
        this.ledgers = ledgers;
        this.writer = new TransactionWriter(transactions, accounts);
        this.clock = clock;
    }

    @Override
    public PostingResult post(PostTransactionCommand cmd) {
        // Step 1: validation and hash, before any lock.
        var txn = LedgerTransaction.open(cmd.ledgerId(), cmd.externalId(), cmd.entries(), cmd.status(),
                cmd.description(), cmd.metadata(), clock.now());
        var hash = RequestHasher.hash(cmd);
        ledgers.findById(cmd.ledgerId()).orElseThrow(() -> new LedgerNotFoundException(cmd.ledgerId()));
        return writer.write(txn, hash, cmd.conditions());
    }
}
