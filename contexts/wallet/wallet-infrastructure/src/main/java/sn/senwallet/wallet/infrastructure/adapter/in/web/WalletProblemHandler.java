package sn.senwallet.wallet.infrastructure.adapter.in.web;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import sn.senwallet.wallet.application.exception.WalletProblem;

/** Errors as RFC 9457 problem details, {@code type = urn:senwallet:problem:<code>}. */
@RestControllerAdvice
public class WalletProblemHandler extends ResponseEntityExceptionHandler {

    static final String TYPE_PREFIX = "urn:senwallet:problem:";

    @ExceptionHandler(WalletProblem.class)
    ProblemDetail walletProblem(WalletProblem problem) {
        var body = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(problem.status()), problem.getMessage());
        body.setType(URI.create(TYPE_PREFIX + problem.code()));
        body.setTitle(problem.code().replace('_', ' '));
        body.setProperty("code", problem.code());
        if (problem.field() != null) {
            body.setProperty("field", problem.field());
        }
        return body;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail illegalArgument(IllegalArgumentException exception) {
        var body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        body.setType(URI.create(TYPE_PREFIX + "invalid_request"));
        body.setTitle("invalid request");
        body.setProperty("code", "invalid_request");
        return body;
    }
}
