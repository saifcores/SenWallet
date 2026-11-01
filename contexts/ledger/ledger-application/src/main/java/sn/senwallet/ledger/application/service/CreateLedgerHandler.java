package sn.senwallet.ledger.application.service;

import org.springframework.transaction.annotation.Transactional;
import sn.senwallet.ledger.application.command.CreateLedgerCommand;
import sn.senwallet.ledger.application.port.in.CreateLedgerUseCase;
import sn.senwallet.ledger.application.port.out.ClockPort;
import sn.senwallet.ledger.domain.model.ledger.Ledger;
import sn.senwallet.ledger.domain.repository.LedgerRepository;

@Transactional
public class CreateLedgerHandler implements CreateLedgerUseCase {

    private final LedgerRepository ledgers;
    private final ClockPort clock;

    public CreateLedgerHandler(LedgerRepository ledgers, ClockPort clock) {
        this.ledgers = ledgers;
        this.clock = clock;
    }

    @Override
    public Ledger create(CreateLedgerCommand cmd) {
        var ledger = Ledger.create(cmd.name(), cmd.environment(), clock.now());
        ledgers.insert(ledger);
        return ledger;
    }
}
