package sn.senwallet.payments.domain.model;

import java.util.Locale;

/** Normalised status of a simulator call or callback. */
public enum RailStatus {
    SUCCEEDED,
    FAILED,
    PENDING,
    UNKNOWN;

    /** Maps simulator words. PI-SPI uses {@code posted}; mobile money uses {@code succeeded}. */
    public static RailStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "succeeded", "posted", "completed", "success" -> SUCCEEDED;
            case "failed", "rejected", "failure" -> FAILED;
            case "pending", "accepted", "processing" -> PENDING;
            default -> UNKNOWN;
        };
    }
}
