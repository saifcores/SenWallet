package sn.senwallet.payments.infrastructure.adapter.in.web;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import sn.senwallet.payments.application.exception.PaymentProblem;

@RestControllerAdvice
public class PaymentProblemHandler {

    static final String TYPE_PREFIX = "urn:senwallet:problem:";

    @ExceptionHandler(PaymentProblem.class)
    ProblemDetail paymentProblem(PaymentProblem problem) {
        var body = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(problem.status()), problem.getMessage());
        body.setType(URI.create(TYPE_PREFIX + problem.code()));
        body.setTitle(problem.code().replace('_', ' '));
        body.setProperty("code", problem.code());
        if (problem.field() != null) {
            body.setProperty("field", problem.field());
        }
        return body;
    }

    static ResponseEntity<String> json(int status, String body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
