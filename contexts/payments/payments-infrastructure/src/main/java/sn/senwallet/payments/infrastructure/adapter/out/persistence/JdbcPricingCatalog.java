package sn.senwallet.payments.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import sn.senwallet.payments.application.port.out.PricingCatalog;

@Repository
public class JdbcPricingCatalog implements PricingCatalog {

    private final JdbcClient jdbc;

    public JdbcPricingCatalog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long monthlyTopupCap(Instant at) {
        return jdbc.sql("""
                SELECT amount FROM limit_policies
                WHERE operation = 'topup_monthly' AND valid_from <= :at
                ORDER BY valid_from DESC
                LIMIT 1
                """)
                .param("at", Columns.timestamp(at))
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new IllegalStateException("no monthly top-up cap"));
    }

    @Override
    public long cashoutFee(long amount, Instant at) {
        var rate = jdbc.sql("""
                SELECT rate FROM fee_schedules
                WHERE operation = 'cashout' AND valid_from <= :at
                ORDER BY valid_from DESC
                LIMIT 1
                """)
                .param("at", Columns.timestamp(at))
                .query(BigDecimal.class)
                .optional()
                .orElseThrow(() -> new IllegalStateException("no cash-out fee schedule"));
        return BigDecimal.valueOf(amount).multiply(rate).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
