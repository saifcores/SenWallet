package sn.senwallet.ledger.infrastructure.adapter.out.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Keeps monthly {@code ledger_entries} partitions created ahead of time, so no
 * entry lands in the default partition (ADR-19). Run at startup and daily.
 */
public class PartitionMaintainer {

    private static final Logger LOG = LoggerFactory.getLogger(PartitionMaintainer.class);

    private final JdbcClient jdbc;
    private final int monthsAhead;

    public PartitionMaintainer(JdbcClient jdbc, int monthsAhead) {
        if (monthsAhead < 1) {
            throw new IllegalArgumentException("monthsAhead must be >= 1, got " + monthsAhead);
        }
        this.jdbc = jdbc;
        this.monthsAhead = monthsAhead;
    }

    /** Returns the number of partitions created. */
    public int ensurePartitions() {
        int created = jdbc.sql("SELECT ensure_entry_partitions(:monthsAhead)")
                .param("monthsAhead", monthsAhead)
                .query(Integer.class)
                .single();
        if (created > 0) {
            LOG.info("created {} ledger_entries partition(s)", created);
        }
        return created;
    }

    /** Rows in the default partition; anything but 0 blocks the creation of the matching month. */
    public long defaultPartitionRows() {
        return jdbc.sql("SELECT count(*) FROM ledger_entries WHERE tableoid = 'ledger_entries_default'::regclass")
                .query(Long.class)
                .single();
    }
}
