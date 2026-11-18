package sn.senwallet.payments.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import sn.senwallet.payments.domain.exception.IllegalPaymentTransition;

/**
 * One external movement. Transitions never go backwards. A timeout becomes
 * {@link State#UNKNOWN} and keeps the ledger hold; only a certain failure
 * archives it.
 */
public final class PaymentOrder {

    public enum Type {
        TOPUP,
        CASHOUT,
        PISPI_OUT,
        PISPI_IN
    }

    public enum State {
        CREATED,
        HOLD_PLACED,
        SUBMITTED,
        COMPLETED,
        FAILED,
        REJECTED,
        UNKNOWN,
        MANUAL_REVIEW;

        public boolean terminal() {
            return this == COMPLETED || this == FAILED || this == REJECTED || this == MANUAL_REVIEW;
        }
    }

    public enum Provider {
        WAVE,
        ORANGE,
        PISPI
    }

    private final UUID id;
    private final Type type;
    private final State state;
    private final UUID walletId;
    private final long amount;
    private final long fee;
    private final String currency;
    private final Provider provider;
    private final String providerRef;
    private final UUID ledgerTxnId;
    private final int attempts;
    private final Instant nextCheckAt;
    private final boolean railSubmitted;
    private final int version;
    private final String phone;
    private final String alias;
    private final Instant createdAt;
    private final Instant updatedAt;

    private PaymentOrder(
            UUID id,
            Type type,
            State state,
            UUID walletId,
            long amount,
            long fee,
            String currency,
            Provider provider,
            String providerRef,
            UUID ledgerTxnId,
            int attempts,
            Instant nextCheckAt,
            boolean railSubmitted,
            int version,
            String phone,
            String alias,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.state = Objects.requireNonNull(state, "state");
        this.walletId = walletId;
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (fee < 0 || fee >= amount) {
            throw new IllegalArgumentException("fee must be zero or less than the amount");
        }
        this.amount = amount;
        this.fee = fee;
        this.currency = Objects.requireNonNull(currency, "currency");
        if (!"XOF".equals(currency)) {
            throw new IllegalArgumentException("currency must be XOF");
        }
        this.provider = Objects.requireNonNull(provider, "provider");
        this.providerRef = providerRef;
        this.ledgerTxnId = ledgerTxnId;
        if (attempts < 0) {
            throw new IllegalArgumentException("attempts must not be negative");
        }
        this.attempts = attempts;
        this.nextCheckAt = nextCheckAt;
        this.railSubmitted = railSubmitted;
        this.version = version;
        this.phone = phone;
        this.alias = alias;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (state == State.COMPLETED && ledgerTxnId == null) {
            throw new IllegalArgumentException("a completed order has a ledger transaction");
        }
        if (type != Type.PISPI_IN && walletId == null) {
            throw new IllegalArgumentException("walletId is required");
        }
    }

    public static PaymentOrder open(
            UUID id,
            Type type,
            UUID walletId,
            long amount,
            long fee,
            Provider provider,
            String phone,
            String alias,
            Instant at) {
        if (type == Type.PISPI_IN) {
            throw new IllegalArgumentException("inbound credits are not opened as sagas");
        }
        return new PaymentOrder(id, type, State.CREATED, walletId, amount, fee, "XOF", provider, null, null, 0, null,
                false, 0, phone, alias, at, at);
    }

    /** A PI-SPI credit already posted. {@code walletId} is null when the alias is unknown. */
    public static PaymentOrder inbound(
            UUID id,
            UUID walletId,
            long amount,
            String providerRef,
            UUID ledgerTxnId,
            String alias,
            Instant at) {
        return new PaymentOrder(id, Type.PISPI_IN, State.COMPLETED, walletId, amount, 0, "XOF", Provider.PISPI,
                providerRef, ledgerTxnId, 0, null, true, 0, null, alias, at, at);
    }

    public static PaymentOrder rehydrate(
            UUID id,
            Type type,
            State state,
            UUID walletId,
            long amount,
            long fee,
            String currency,
            Provider provider,
            String providerRef,
            UUID ledgerTxnId,
            int attempts,
            Instant nextCheckAt,
            boolean railSubmitted,
            int version,
            String phone,
            String alias,
            Instant createdAt,
            Instant updatedAt) {
        return new PaymentOrder(id, type, state, walletId, amount, fee, currency, provider, providerRef, ledgerTxnId,
                attempts, nextCheckAt, railSubmitted, version, phone, alias, createdAt, updatedAt);
    }

    public PaymentOrder holdPlaced(UUID ledgerTxnId, Instant at) {
        require(State.CREATED, "place a hold");
        Objects.requireNonNull(ledgerTxnId, "ledgerTxnId");
        return copy(State.HOLD_PLACED, ledgerTxnId, providerRef, attempts, nextCheckAt, railSubmitted, at);
    }

    public PaymentOrder rejected(Instant at) {
        if (state != State.CREATED && state != State.HOLD_PLACED) {
            throw new IllegalPaymentTransition(state, "reject");
        }
        return copy(State.REJECTED, ledgerTxnId, providerRef, attempts, null, railSubmitted, at);
    }

    /** The rail HTTP call is about to start. A replay must not send it again. */
    public PaymentOrder railSubmitted(Instant at) {
        require(State.HOLD_PLACED, "submit to the rail");
        return copy(state, ledgerTxnId, providerRef, attempts, nextCheckAt, true, at);
    }

    public PaymentOrder submitted(String providerRef, Instant nextCheckAt, Instant at) {
        require(State.HOLD_PLACED, "mark submitted");
        return copy(State.SUBMITTED, ledgerTxnId, providerRef, attempts, nextCheckAt, true, at);
    }

    public PaymentOrder unknown(Instant nextCheckAt, Instant at) {
        if (state != State.HOLD_PLACED && state != State.SUBMITTED) {
            throw new IllegalPaymentTransition(state, "mark unknown");
        }
        return copy(State.UNKNOWN, ledgerTxnId, providerRef, attempts, nextCheckAt, railSubmitted, at);
    }

    public PaymentOrder completed(Instant at) {
        if (state != State.SUBMITTED && state != State.UNKNOWN && state != State.HOLD_PLACED) {
            throw new IllegalPaymentTransition(state, "complete");
        }
        if (ledgerTxnId == null) {
            throw new IllegalPaymentTransition(state, "complete without a hold");
        }
        return copy(State.COMPLETED, ledgerTxnId, providerRef, attempts, null, true, at);
    }

    public PaymentOrder withProviderRef(String providerRef) {
        return copy(state, ledgerTxnId, providerRef, attempts, nextCheckAt, railSubmitted, updatedAt);
    }

    public PaymentOrder failed(Instant at) {
        if (state != State.SUBMITTED && state != State.UNKNOWN) {
            throw new IllegalPaymentTransition(state, "fail");
        }
        return copy(State.FAILED, ledgerTxnId, providerRef, attempts, null, railSubmitted, at);
    }

    /** Still pending. The first miss from {@link State#SUBMITTED} becomes unknown. */
    public PaymentOrder inquiryMissed(Instant nextCheckAt, int maxAttempts, Instant at) {
        if (state == State.SUBMITTED) {
            return copy(State.UNKNOWN, ledgerTxnId, providerRef, attempts + 1, nextCheckAt, railSubmitted, at);
        }
        require(State.UNKNOWN, "record an inquiry");
        var nextAttempts = attempts + 1;
        if (nextAttempts >= maxAttempts) {
            return copy(State.MANUAL_REVIEW, ledgerTxnId, providerRef, nextAttempts, null, railSubmitted, at);
        }
        return copy(State.UNKNOWN, ledgerTxnId, providerRef, nextAttempts, nextCheckAt, railSubmitted, at);
    }

    private void require(State expected, String action) {
        if (state != expected) {
            throw new IllegalPaymentTransition(state, action);
        }
    }

    private PaymentOrder copy(
            State next,
            UUID ledgerTxnId,
            String providerRef,
            int attempts,
            Instant nextCheckAt,
            boolean railSubmitted,
            Instant at) {
        return new PaymentOrder(id, type, next, walletId, amount, fee, currency, provider, providerRef, ledgerTxnId,
                attempts, nextCheckAt, railSubmitted, version, phone, alias, createdAt, at);
    }

    public UUID id() {
        return id;
    }

    public Type type() {
        return type;
    }

    public State state() {
        return state;
    }

    public UUID walletId() {
        return walletId;
    }

    public long amount() {
        return amount;
    }

    public long fee() {
        return fee;
    }

    public String currency() {
        return currency;
    }

    public Provider provider() {
        return provider;
    }

    public String providerRef() {
        return providerRef;
    }

    public UUID ledgerTxnId() {
        return ledgerTxnId;
    }

    public int attempts() {
        return attempts;
    }

    public Instant nextCheckAt() {
        return nextCheckAt;
    }

    public boolean railSubmitted() {
        return railSubmitted;
    }

    public int version() {
        return version;
    }

    public String phone() {
        return phone;
    }

    public String alias() {
        return alias;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public String externalId() {
        var prefix = switch (type) {
            case TOPUP -> "topup:";
            case CASHOUT -> "cashout:";
            case PISPI_OUT -> "pispi-out:";
            case PISPI_IN -> "pispi-in:";
        };
        return prefix + (type == Type.PISPI_IN ? providerRef : id);
    }
}
