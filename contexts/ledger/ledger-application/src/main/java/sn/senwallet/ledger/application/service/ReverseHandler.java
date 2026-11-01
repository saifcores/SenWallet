package sn.senwallet.ledger.application.service;

import org.springframework.transaction.annotation.Transactional;
import sn.senwallet.ledger.application.command.ReverseTransactionCommand;
import sn.senwallet.ledger.application.exception.TransactionNotFoundException;
import sn.senwallet.ledger.application.port.in.PostingResult;
import sn.senwallet.ledger.application.port.in.ReverseTransactionUseCase;
import sn.senwallet.ledger.application.port.out.ClockPort;
import sn.senwallet.ledger.domain.repository.LedgerAccountRepository;
import sn.senwallet.ledger.domain.repository.LedgerTransactionRepository;

@Transactional
public class ReverseHandler implements ReverseTransactionUseCase {

    private final LedgerTransactionRepository transactions;
    private final TransactionWriter writer;
    private final ClockPort clock;

    public ReverseHandler(
            LedgerTransactionRepository transactions, LedgerAccountRepository accounts, ClockPort clock) {
        this.transactions = transactions;
        this.writer = new TransactionWriter(transactions, accounts);
        this.clock = clock;
    }

    @Override
    public PostingResult reverse(ReverseTransactionCommand cmd) {
        var original = transactions.findById(cmd.transactionId())
                .orElseThrow(() -> new TransactionNotFoundException(cmd.transactionId()));
        var reversal = original.reversal(cmd.effectiveExternalId(), clock.now());
        return writer.write(reversal, RequestHasher.hash(cmd), cmd.conditions());
    }
}
