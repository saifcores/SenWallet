package sn.senwallet.ledger.infrastructure.adapter.out.persistence;

import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import sn.senwallet.ledger.application.port.out.BalanceHistoryPort;
import sn.senwallet.ledger.domain.model.account.Balances;
import sn.senwallet.ledger.domain.model.account.LedgerAccount;

/**
 * Counters as of a past instant: a transaction counts as posted once its
 * {@code posted_at} is reached, and as pending from its {@code effective_at}
 * until it is posted or archived (ADR-18).
 */
@Component
public class JdbcBalanceHistory implements BalanceHistoryPort {

    private final JdbcClient jdbc;

    public JdbcBalanceHistory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Balances balancesAsOf(LedgerAccount account, Instant asOf) {
        return jdbc.sql("""
                WITH state AS (
                    SELECT e.direction, e.amount,
                           t.posted_at <= :asOf AS posted,
                           t.effective_at <= :asOf
                               AND (t.posted_at IS NULL OR t.posted_at > :asOf)
                               AND (t.archived_at IS NULL OR t.archived_at > :asOf) AS pending
                    FROM ledger_entries e JOIN ledger_transactions t ON t.id = e.transaction_id
                    WHERE e.account_id = :accountId
                )
                SELECT coalesce(sum(amount) FILTER (WHERE posted AND direction = 'debit'), 0)   AS posted_dr,
                       coalesce(sum(amount) FILTER (WHERE posted AND direction = 'credit'), 0)  AS posted_cr,
                       coalesce(sum(amount) FILTER (WHERE pending AND direction = 'debit'), 0)  AS pending_dr,
                       coalesce(sum(amount) FILTER (WHERE pending AND direction = 'credit'), 0) AS pending_cr
                FROM state
                """)
                .param("accountId", account.id().value())
                .param("asOf", Sql.timestamp(asOf))
                .query((rs, row) -> Sql.balances(rs, account.currency(), 0))
                .single();
    }
}
