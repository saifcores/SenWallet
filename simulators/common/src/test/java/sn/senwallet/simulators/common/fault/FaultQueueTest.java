package sn.senwallet.simulators.common.fault;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FaultQueueTest {

    @Test
    void consumeDecrementsCountAndYieldsEffect() {
        FaultQueue queue = new FaultQueue();
        queue.enqueue(new FaultSpec(
                "disbursements",
                FaultMode.REJECT,
                1.0,
                0,
                2));

        assertThat(queue.consume("disbursements"))
                .isInstanceOf(FaultEffect.Reject.class);
        assertThat(queue.size()).isEqualTo(1);
        assertThat(queue.consume("disbursements"))
                .isInstanceOf(FaultEffect.Reject.class);
        assertThat(queue.size()).isZero();
        assertThat(queue.consume("disbursements"))
                .isInstanceOf(FaultEffect.None.class);
    }

    @Test
    void unmatchedOperationLeavesQueueIntact() {
        FaultQueue queue = new FaultQueue();
        queue.enqueue(new FaultSpec(
                "disbursements",
                FaultMode.LATENCY,
                1.0,
                50,
                1));

        assertThat(queue.consume("collections"))
                .isInstanceOf(FaultEffect.None.class);
        assertThat(queue.size()).isEqualTo(1);
    }
}
