package sn.senwallet.simulators.bank;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import sn.senwallet.simulators.bank.LedgerBook.Credit;
import sn.senwallet.simulators.common.FaultSupport;
import sn.senwallet.simulators.common.fault.FaultEffect;
import sn.senwallet.simulators.common.fault.FaultQueue;

@Service
public class BankService {

    private final LedgerBook book;
    private final StatementExporter exporter;
    private final FaultQueue faultQueue;
    private final String bankName;

    public BankService(
            LedgerBook book,
            StatementExporter exporter,
            FaultQueue faultQueue,
            @org.springframework.beans.factory.annotation.Value("${simulator.bank-name}")
                    String bankName) {
        this.book = book;
        this.exporter = exporter;
        this.faultQueue = faultQueue;
        this.bankName = bankName;
    }

    public BankDtos.CreditResponse credit(BankDtos.CreditRequest request) {
        if (request.account() == null || request.account().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "account required");
        }
        if (request.amount() == null || request.amount() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount required");
        }
        FaultEffect effect = faultQueue.consume("credits");
        FaultSupport.applyLatency(effect);
        if (effect instanceof FaultEffect.Reject) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "credit rejected");
        }
        String reference = request.reference() == null || request.reference().isBlank()
                ? UUID.randomUUID().toString()
                : request.reference();
        Credit credit = book.credit(
                request.account(),
                reference,
                request.amount(),
                request.currency() == null || request.currency().isBlank()
                        ? "XOF"
                        : request.currency(),
                request.description());
        return new BankDtos.CreditResponse(
                credit.id(),
                credit.account(),
                credit.reference(),
                credit.amount(),
                credit.currency(),
                credit.createdAt());
    }

    public BankDtos.StatementResponse statement(String account, LocalDate date) {
        if (account == null || account.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "account required");
        }
        FaultEffect effect = faultQueue.consume("statements");
        FaultSupport.applyLatency(effect);
        if (effect instanceof FaultEffect.Reject) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "statement rejected");
        }
        List<Credit> credits = book.creditsOn(account, date);
        String objectKey = exporter.export(account, date, credits, effect);
        int lineCount = switch (effect) {
            case FaultEffect.MissingStatementLine ignored -> Math.max(0, credits.size() - 1);
            default -> credits.size();
        };
        return new BankDtos.StatementResponse(
                bankName,
                account,
                date.toString(),
                objectKey,
                exporter.bucket(),
                lineCount);
    }
}
