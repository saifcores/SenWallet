package sn.senwallet.simulators.common.fault;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Fault modes exposed by {@code POST /control/faults}. */
public enum FaultMode {
    REJECT,
    LATENCY,
    TIMEOUT_THEN_SUCCESS,
    TIMEOUT_THEN_FAILURE,
    DUPLICATE_CALLBACK,
    OUT_OF_ORDER_CALLBACK,
    FLOAT_EXHAUSTED,
    MISSING_STATEMENT_LINE,
    WRONG_AMOUNT;

    @JsonValue
    public String wire() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static FaultMode fromWire(String value) {
        return FaultMode.valueOf(value.trim().toUpperCase());
    }
}
