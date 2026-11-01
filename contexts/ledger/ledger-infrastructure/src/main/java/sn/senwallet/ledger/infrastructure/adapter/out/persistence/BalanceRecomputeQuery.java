package sn.senwallet.ledger.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.senwallet.ledger.domain.model.account.Balances;
import sn.senwallet.shared.id.AccountId;

/**
 * Rebuilds the four counters of every account from {@code ledger_entries ⋈
 * ledger_transactions} and compares them with {@code account_balances}
 * (dossier section 3, exercise 3; the nightly drift scan from M8).
 */
@Component
public class BalanceRecomputeQuery {

    /** Stored and recomputed counters of one account; lock versions are not compared. */
    public record Comparison(AccountId accountId, Balances stored, Balances recomputed) {

        public boolean drifted() {
            return !stored.postedDebits().equals(recomputed.postedDebits())
                    || !stored.postedCredits().equals(recomputed.postedCredits())
                    || !stored.pendingDebits().equals(recomputed.pendingDebits())
                    || !stored.pendingCredits().equals(recomputed.pendingCredits());
        }
    }

    private final JdbcClient jdbc;

    public BalanceRecomputeQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<Comparison> compareAll() {
        return jdbc.sql("""
                WITH recomputed AS (
                    SELECT e.account_id,
                           sum(e.amount) FILTER (WHERE t.status = 'posted'  AND e.direction = 'debit')  AS posted_dr,
                           sum(e.amount) FILTER (WHERE t.status = 'posted'  AND e.direction = 'credit') AS posted_cr,
                           sum(e.amount) FILTER (WHERE t.status = 'pending' AND e.direction = 'debit')  AS pending_dr,
                           sum(e.amount) FILTER (WHERE t.status = 'pending' AND e.direction = 'credit') AS pending_cr
                    FROM ledger_entries e JOIN ledger_transactions t ON t.id = e.transaction_id
                    GROUP BY e.account_id
                )
                SELECT a.id, a.currency,
                       b.posted_dr, b.posted_cr, b.pending_dr, b.pending_cr,
                       coalesce(r.posted_dr, 0)  AS r_posted_dr,  coalesce(r.posted_cr, 0)  AS r_posted_cr,
                       coalesce(r.pending_dr, 0) AS r_pending_dr, coalesce(r.pending_cr, 0) AS r_pending_cr
                FROM ledger_accounts a
                JOIN account_balances b ON b.account_id = a.id
                LEFT JOIN recomputed r ON r.account_id = a.id
                ORDER BY a.id
                """)
                .query((rs, row) -> {
                    var currency = Sql.currency(rs, "currency");
                    var recomputed = new Balances(
                            Sql.money(rs.getLong("r_posted_dr"), currency),
                            Sql.money(rs.getLong("r_posted_cr"), currency),
                            Sql.money(rs.getLong("r_pending_dr"), currency),
                            Sql.money(rs.getLong("r_pending_cr"), currency),
                            0);
                    return new Comparison(new AccountId(rs.getObject("id", UUID.class)),
                            Sql.balances(rs, currency, 0), recomputed);
                })
                .list();
    }
}
