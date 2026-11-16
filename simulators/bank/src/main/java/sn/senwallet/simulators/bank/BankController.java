package sn.senwallet.simulators.bank;

import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BankController {

    private final BankService service;

    public BankController(BankService service) {
        this.service = service;
    }

    @PostMapping("/control/credit")
    @ResponseStatus(HttpStatus.CREATED)
    public BankDtos.CreditResponse credit(@RequestBody BankDtos.CreditRequest request) {
        return service.credit(request);
    }

    @GetMapping("/statements")
    public BankDtos.StatementResponse statements(
            @RequestParam String account,
            @RequestParam String date) {
        return service.statement(account, LocalDate.parse(date));
    }
}
