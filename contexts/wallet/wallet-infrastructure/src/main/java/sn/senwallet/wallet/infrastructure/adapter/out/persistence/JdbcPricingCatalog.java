package sn.senwallet.wallet.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import sn.senwallet.shared.money.Money;
import sn.senwallet.wallet.application.port.out.PricingCatalog;
import sn.senwallet.wallet.domain.model.FeeSchedule;
import sn.senwallet.wallet.domain.model.KycTier;
import sn.senwallet.wallet.domain.model.LimitPolicy;

@Repository
public class JdbcPricingCatalog implements PricingCatalog {

    private final JdbcClient jdbc;

    public JdbcPricingCatalog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public LimitPolicy limit(KycTier tier, Instant at) {
        return jdbc.sql("""
                SELECT holding_cap, valid_from
                FROM limit_policies
                WHERE tier = CAST(:tier AS kyc_tier) AND valid_from <= :at
                ORDER BY valid_from DESC
                LIMIT 1
                """)
                .param("tier", Columns.label(tier))
                .param("at", Columns.timestamp(at))
                .query((rs, row) -> new LimitPolicy(tier, Money.xof(rs.getLong("holding_cap")),
                        Columns.instant(rs, "valid_from")))
                .optional()
                .orElseThrow(() -> new IllegalStateException("no limit policy for " + tier + " at " + at));
    }

    @Override
    public FeeSchedule p2pFee(Instant at) {
        return jdbc.sql("""
                SELECT rate, valid_from
                FROM fee_schedules
                WHERE operation = 'p2p' AND valid_from <= :at
                ORDER BY valid_from DESC
                LIMIT 1
                """)
                .param("at", Columns.timestamp(at))
                .query((rs, row) -> new FeeSchedule(rs.getObject("rate", BigDecimal.class),
                        Columns.instant(rs, "valid_from")))
                .optional()
                .orElseThrow(() -> new IllegalStateException("no P2P fee schedule at " + at));
    }
}
