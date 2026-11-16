package sn.senwallet.simulators.common;

import sn.senwallet.simulators.common.fault.FaultEffect;

/** Shared helpers for applying latency and mapping reject responses. */
public final class FaultSupport {

    private FaultSupport() {
    }

    public static void applyLatency(FaultEffect effect) {
        long delayMs = switch (effect) {
            case FaultEffect.Latency latency -> latency.delayMs();
            default -> 0L;
        };
        sleep(delayMs);
    }

    public static void sleep(long delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
