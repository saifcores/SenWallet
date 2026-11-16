package sn.senwallet.simulators.common.fault;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;

/**
 * FIFO queue of fault specs keyed by operation name. Thread-safe; each matching
 * consume decrements {@code count} and applies {@code probability}.
 */
public final class FaultQueue {

    private final List<FaultSpec> pending = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();

    public void enqueue(FaultSpec spec) {
        lock.lock();
        try {
            pending.add(spec);
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lock.lock();
        try {
            pending.clear();
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return pending.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Takes the oldest matching fault for {@code operation}, if any. Returns
     * {@link FaultEffect.None} when the queue is empty or probability misses.
     */
    public FaultEffect consume(String operation) {
        lock.lock();
        try {
            for (int i = 0; i < pending.size(); i++) {
                FaultSpec spec = pending.get(i);
                if (!spec.operation().equals(operation)) {
                    continue;
                }
                if (spec.count() <= 1) {
                    pending.remove(i);
                } else {
                    pending.set(i, spec.withCount(spec.count() - 1));
                }
                if (ThreadLocalRandom.current().nextDouble() > spec.probability()) {
                    return new FaultEffect.None();
                }
                return toEffect(spec);
            }
            return new FaultEffect.None();
        } finally {
            lock.unlock();
        }
    }

    private static FaultEffect toEffect(FaultSpec spec) {
        return switch (spec.mode()) {
            case REJECT -> new FaultEffect.Reject();
            case LATENCY -> new FaultEffect.Latency(spec.delayMs());
            case TIMEOUT_THEN_SUCCESS -> new FaultEffect.TimeoutThenSuccess(spec.delayMs());
            case TIMEOUT_THEN_FAILURE -> new FaultEffect.TimeoutThenFailure(spec.delayMs());
            case DUPLICATE_CALLBACK -> new FaultEffect.DuplicateCallback(spec.delayMs());
            case OUT_OF_ORDER_CALLBACK -> new FaultEffect.OutOfOrderCallback(spec.delayMs());
            case FLOAT_EXHAUSTED -> new FaultEffect.FloatExhausted();
            case MISSING_STATEMENT_LINE -> new FaultEffect.MissingStatementLine();
            case WRONG_AMOUNT -> new FaultEffect.WrongAmount();
        };
    }
}
