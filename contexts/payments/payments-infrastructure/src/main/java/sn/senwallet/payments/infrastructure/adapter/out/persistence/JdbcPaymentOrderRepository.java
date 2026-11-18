package sn.senwallet.payments.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import sn.senwallet.payments.application.port.out.PaymentOrderRepository;
import sn.senwallet.payments.domain.model.PaymentOrder;

@Repository
public class JdbcPaymentOrderRepository implements PaymentOrderRepository {

    private final JdbcClient jdbc;

    public JdbcPaymentOrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(PaymentOrder order) {
        bind(jdbc.sql("""
                INSERT INTO payment_orders (
                    id, type, state, wallet_id, amount, fee, currency, provider, provider_ref,
                    ledger_txn_id, attempts, next_check_at, rail_submitted, version, phone, alias,
                    created_at, updated_at)
                VALUES (
                    :id, :type, :state, :walletId, :amount, :fee, :currency, :provider, :providerRef,
                    :ledgerTxnId, :attempts, :nextCheckAt, :railSubmitted, :version, :phone, :alias,
                    :createdAt, :updatedAt)
                """), order).update();
    }

    @Override
    public void update(PaymentOrder order) {
        var updated = bind(jdbc.sql("""
                UPDATE payment_orders SET
                    state = :state,
                    provider_ref = :providerRef,
                    ledger_txn_id = :ledgerTxnId,
                    attempts = :attempts,
                    next_check_at = :nextCheckAt,
                    rail_submitted = :railSubmitted,
                    version = version + 1,
                    phone = :phone,
                    alias = :alias,
                    updated_at = :updatedAt
                WHERE id = :id
                """), order).update();
        if (updated == 0) {
            throw new IllegalStateException("payment order " + order.id() + " disappeared");
        }
    }

    @Override
    public Optional<PaymentOrder> findById(UUID id) {
        return jdbc.sql("SELECT * FROM payment_orders WHERE id = :id")
                .param("id", id)
                .query((rs, row) -> map(rs))
                .optional();
    }

    @Override
    public Optional<PaymentOrder> findByProviderRef(String providerRef) {
        return jdbc.sql("SELECT * FROM payment_orders WHERE provider_ref = :ref")
                .param("ref", providerRef)
                .query((rs, row) -> map(rs))
                .optional();
    }

    @Override
    @Transactional
    public boolean insertWithinMonthlyCap(PaymentOrder order, long cap, Instant start, Instant end) {
        jdbc.sql("SELECT pg_advisory_xact_lock(:key)")
                .param("key", order.walletId().getMostSignificantBits())
                .query((rs, row) -> 1)
                .single();
        var used = jdbc.sql("""
                SELECT COALESCE(SUM(amount), 0)
                FROM payment_orders
                WHERE wallet_id = :walletId
                  AND type = 'topup'
                  AND state NOT IN ('rejected', 'failed')
                  AND created_at >= :start
                  AND created_at < :end
                """)
                .param("walletId", order.walletId())
                .param("start", Columns.timestamp(start))
                .param("end", Columns.timestamp(end))
                .query(Long.class)
                .single();
        if (used + order.amount() > cap) {
            return false;
        }
        insert(order);
        return true;
    }

    @Override
    @Transactional
    public boolean claimRail(UUID orderId) {
        var updated = jdbc.sql("""
                UPDATE payment_orders
                SET rail_submitted = true, updated_at = now()
                WHERE id = :id AND rail_submitted = false
                """)
                .param("id", orderId)
                .update();
        return updated == 1;
    }

    @Override
    public java.util.List<PaymentOrder> lockDue(int limit) {
        return jdbc.sql("""
                SELECT * FROM payment_orders
                WHERE state IN ('submitted', 'unknown')
                  AND next_check_at IS NOT NULL
                  AND next_check_at <= now()
                ORDER BY next_check_at
                FOR UPDATE SKIP LOCKED
                LIMIT :limit
                """)
                .param("limit", limit)
                .query((rs, row) -> map(rs))
                .list();
    }

    private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, PaymentOrder order) {
        return spec.param("id", order.id())
                .param("type", Columns.label(order.type()))
                .param("state", Columns.label(order.state()))
                .param("walletId", order.walletId())
                .param("amount", order.amount())
                .param("fee", order.fee())
                .param("currency", order.currency())
                .param("provider", Columns.label(order.provider()))
                .param("providerRef", order.providerRef())
                .param("ledgerTxnId", order.ledgerTxnId())
                .param("attempts", order.attempts())
                .param("nextCheckAt", Columns.timestamp(order.nextCheckAt()))
                .param("railSubmitted", order.railSubmitted())
                .param("version", order.version())
                .param("phone", order.phone())
                .param("alias", order.alias())
                .param("createdAt", Columns.timestamp(order.createdAt()))
                .param("updatedAt", Columns.timestamp(order.updatedAt()));
    }

    private static PaymentOrder map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return PaymentOrder.rehydrate(
                rs.getObject("id", UUID.class),
                Columns.enumOf(PaymentOrder.Type.class, rs.getString("type")),
                Columns.enumOf(PaymentOrder.State.class, rs.getString("state")),
                rs.getObject("wallet_id", UUID.class),
                rs.getLong("amount"),
                rs.getLong("fee"),
                rs.getString("currency"),
                Columns.enumOf(PaymentOrder.Provider.class, rs.getString("provider")),
                rs.getString("provider_ref"),
                rs.getObject("ledger_txn_id", UUID.class),
                rs.getInt("attempts"),
                Columns.instant(rs, "next_check_at"),
                rs.getBoolean("rail_submitted"),
                rs.getInt("version"),
                rs.getString("phone"),
                rs.getString("alias"),
                Columns.instant(rs, "created_at"),
                Columns.instant(rs, "updated_at"));
    }
}
