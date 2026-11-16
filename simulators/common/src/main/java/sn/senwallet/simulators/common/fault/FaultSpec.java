package sn.senwallet.simulators.common.fault;

/**
 * One injectable fault. {@code count} is how many matching operations consume
 * this entry; {@code probability} is applied when the entry is consumed.
 */
public record FaultSpec(
        String operation,
        FaultMode mode,
        double probability,
        long delayMs,
        int count) {

    public FaultSpec {
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("operation is required");
        }
        if (mode == null) {
            throw new IllegalArgumentException("mode is required");
        }
        if (probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("probability must be in [0, 1]");
        }
        if (delayMs < 0) {
            throw new IllegalArgumentException("delay_ms must be >= 0");
        }
        if (count < 1) {
            throw new IllegalArgumentException("count must be >= 1");
        }
    }

    public FaultSpec withCount(int nextCount) {
        return new FaultSpec(operation, mode, probability, delayMs, nextCount);
    }
}
