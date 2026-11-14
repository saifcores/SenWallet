package sn.senwallet.simulators.pispi;

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
public class PispiController {

    private final PispiService service;

    public PispiController(PispiService service) {
        this.service = service;
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public PispiDtos.TransferResponse create(@RequestBody PispiDtos.TransferRequest request) {
        return service.outbound(request);
    }

    @GetMapping("/transfers/{id}")
    public PispiDtos.TransferResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/control/inbound")
    @ResponseStatus(HttpStatus.CREATED)
    public PispiDtos.TransferResponse inbound(@RequestBody PispiDtos.InboundRequest request) {
        return service.inbound(request);
    }

    @GetMapping("/reports/settlement")
    public PispiDtos.SettlementReport settlement(@RequestParam String date) {
        return service.settlement(LocalDate.parse(date));
    }
}
