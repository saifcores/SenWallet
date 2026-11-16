package sn.senwallet.simulators.common.fault;

/** Outcome of consuming a fault for one operation. */
public sealed interface FaultEffect {

    record None() implements FaultEffect {
    }

    record Reject() implements FaultEffect {
    }

    record Latency(long delayMs) implements FaultEffect {
    }

    record TimeoutThenSuccess(long delayMs) implements FaultEffect {
    }

    record TimeoutThenFailure(long delayMs) implements FaultEffect {
    }

    record DuplicateCallback(long delayMs) implements FaultEffect {
    }

    record OutOfOrderCallback(long delayMs) implements FaultEffect {
    }

    record FloatExhausted() implements FaultEffect {
    }

    record MissingStatementLine() implements FaultEffect {
    }

    record WrongAmount() implements FaultEffect {
    }
}
