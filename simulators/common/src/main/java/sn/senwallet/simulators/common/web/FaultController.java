package sn.senwallet.simulators.common.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sn.senwallet.simulators.common.fault.FaultQueue;
import sn.senwallet.simulators.common.fault.FaultSpec;

@RestController
@RequestMapping("/control/faults")
public class FaultController {

    private final FaultQueue faultQueue;

    public FaultController(FaultQueue faultQueue) {
        this.faultQueue = faultQueue;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FaultDtos.EnqueueFaultResponse enqueue(
            @RequestBody FaultDtos.EnqueueFaultRequest request) {
        double probability = request.probability() == null ? 1.0 : request.probability();
        long delayMs = request.delayMs() == null ? 0L : request.delayMs();
        int count = request.count() == null ? 1 : request.count();
        FaultSpec spec = new FaultSpec(
                request.operation(),
                request.mode(),
                probability,
                delayMs,
                count);
        faultQueue.enqueue(spec);
        return new FaultDtos.EnqueueFaultResponse(
                spec.operation(),
                spec.mode(),
                spec.probability(),
                spec.delayMs(),
                spec.count());
    }
}
