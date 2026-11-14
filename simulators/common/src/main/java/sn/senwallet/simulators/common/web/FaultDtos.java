package sn.senwallet.simulators.common.web;

import sn.senwallet.simulators.common.fault.FaultMode;

public final class FaultDtos {

    private FaultDtos() {
    }

    public record EnqueueFaultRequest(
            String operation,
            FaultMode mode,
            Double probability,
            Long delayMs,
            Integer count) {
    }

    public record EnqueueFaultResponse(
            String operation,
            FaultMode mode,
            double probability,
            long delayMs,
            int count) {
    }
}
