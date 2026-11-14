package sn.senwallet.simulators.mno;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MnoController {

    private final MnoService service;

    public MnoController(MnoService service) {
        this.service = service;
    }

    @PostMapping("/collections")
    @ResponseStatus(HttpStatus.CREATED)
    public MnoDtos.TransactionResponse collect(@RequestBody MnoDtos.MoneyRequest request) {
        return service.collect(request);
    }

    @PostMapping("/disbursements")
    @ResponseStatus(HttpStatus.CREATED)
    public MnoDtos.TransactionResponse disburse(@RequestBody MnoDtos.MoneyRequest request) {
        return service.disburse(request);
    }

    @GetMapping("/transactions/{id}")
    public MnoDtos.TransactionResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/float")
    public MnoDtos.FloatResponse floatBalance() {
        return service.floatBalance();
    }

    @PostMapping("/control/float")
    public MnoDtos.FloatResponse setFloat(@RequestBody MnoDtos.SetFloatRequest request) {
        return service.setFloat(request);
    }

    @GetMapping("/settlement-files")
    public MnoDtos.SettlementFile settlement(@RequestParam String date) {
        return service.settlement(LocalDate.parse(date));
    }
}
