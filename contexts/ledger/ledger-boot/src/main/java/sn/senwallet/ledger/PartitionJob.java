package sn.senwallet.ledger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import sn.senwallet.ledger.infrastructure.adapter.out.persistence.PartitionMaintainer;

/** Creates upcoming {@code ledger_entries} partitions at startup, then every day (ADR-19). */
@Component
@EnableScheduling
class PartitionJob implements ApplicationRunner {

    private final PartitionMaintainer maintainer;

    PartitionJob(JdbcClient jdbc, @Value("${ledger.partitions.months-ahead:3}") int monthsAhead) {
        this.maintainer = new PartitionMaintainer(jdbc, monthsAhead);
    }

    @Override
    public void run(ApplicationArguments args) {
        maintainer.ensurePartitions();
    }

    @Scheduled(cron = "${ledger.partitions.cron:0 0 3 * * *}", zone = "UTC")
    void daily() {
        maintainer.ensurePartitions();
    }
}
