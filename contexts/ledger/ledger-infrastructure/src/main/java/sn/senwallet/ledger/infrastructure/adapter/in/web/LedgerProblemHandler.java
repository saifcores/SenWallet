package sn.senwallet.ledger.infrastructure.adapter.in.web;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import sn.senwallet.ledger.application.exception.AccountNotFoundException;
import sn.senwallet.ledger.application.exception.IdempotencyConflictException;
import sn.senwallet.ledger.application.exception.LedgerNotFoundException;
import sn.senwallet.ledger.application.exception.TransactionNotFoundException;
import sn.senwallet.ledger.domain.exception.AccountNotPostableException;
import sn.senwallet.ledger.domain.exception.BalanceConditionViolatedException;
import sn.senwallet.ledger.domain.exception.IllegalTransitionException;
import sn.senwallet.ledger.domain.exception.TransactionAlreadyReversedException;
import sn.senwallet.ledger.domain.exception.TransactionNotReversibleException;
import sn.senwallet.ledger.domain.exception.UnbalancedTransactionException;
import sn.senwallet.shared.money.CurrencyMismatchException;

/**
 * Errors as RFC 9457 problem details. {@code type} is
 * {@code urn:senwallet:problem:<code>} and never changes for a given code;
 * clients branch on it, not on {@code detail}.
 */
@RestControllerAdvice
public class LedgerProblemHandler extends ResponseEntityExceptionHandler {

    static final String TYPE_PREFIX = "urn:senwallet:problem:";

    @ExceptionHandler(UnbalancedTransactionException.class)
    ProblemDetail unbalanced(UnbalancedTransactionException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "unbalanced", e.getMessage());
    }

    @ExceptionHandler(BalanceConditionViolatedException.class)
    ProblemDetail conditionViolated(BalanceConditionViolatedException e) {
        var condition = e.condition();
        var problem = problem(HttpStatus.UNPROCESSABLE_CONTENT, "balance_condition_violated", e.getMessage());
        problem.setProperty("account_id", condition.accountId().value());
        problem.setProperty("condition", Map.of(
                "balance", condition.type().name().toLowerCase(Locale.ROOT),
                "operator", condition.operator().name().toLowerCase(Locale.ROOT),
                "amount", condition.threshold().toMinorUnits()));
        problem.setProperty("actual", e.actual().toMinorUnits());
        return problem;
    }

    @ExceptionHandler(AccountNotPostableException.class)
    ProblemDetail accountNotPostable(AccountNotPostableException e) {
        var problem = problem(HttpStatus.UNPROCESSABLE_CONTENT, "account_not_postable", e.getMessage());
        problem.setProperty("account_id", e.accountId().value());
        return problem;
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    ProblemDetail currencyMismatch(CurrencyMismatchException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "currency_mismatch", e.getMessage());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ProblemDetail idempotencyConflict(IdempotencyConflictException e) {
        var problem = problem(HttpStatus.CONFLICT, "idempotency_conflict", e.getMessage());
        problem.setProperty("external_id", e.externalId().value());
        return problem;
    }

    @ExceptionHandler(IllegalTransitionException.class)
    ProblemDetail illegalTransition(IllegalTransitionException e) {
        var problem = problem(HttpStatus.CONFLICT, "illegal_transition", e.getMessage());
        problem.setProperty("from", e.from().name().toLowerCase(Locale.ROOT));
        problem.setProperty("to", e.to().name().toLowerCase(Locale.ROOT));
        return problem;
    }

    @ExceptionHandler(TransactionNotReversibleException.class)
    ProblemDetail notReversible(TransactionNotReversibleException e) {
        return problem(HttpStatus.CONFLICT, "transaction_not_reversible", e.getMessage());
    }

    @ExceptionHandler(TransactionAlreadyReversedException.class)
    ProblemDetail alreadyReversed(TransactionAlreadyReversedException e) {
        return problem(HttpStatus.CONFLICT, "transaction_already_reversed", e.getMessage());
    }

    @ExceptionHandler({ LedgerNotFoundException.class, AccountNotFoundException.class,
            TransactionNotFoundException.class })
    ProblemDetail notFound(RuntimeException e) {
        return problem(HttpStatus.NOT_FOUND, "not_found", e.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail invalidRequest(InvalidRequestException e) {
        var problem = problem(HttpStatus.BAD_REQUEST, "invalid_request", e.getMessage());
        problem.setProperty("field", e.field());
        return problem;
    }

    /** Domain argument checks: a condition on an async account, a blank name, etc. */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail illegalArgument(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "invalid_request", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + code));
        problem.setTitle(code.replace('_', ' '));
        problem.setProperty("code", code);
        return problem;
    }
}
