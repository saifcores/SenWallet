package sn.senwallet.payments.infrastructure.adapter.out.persistence;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import sn.senwallet.payments.application.exception.PaymentProblem;
import sn.senwallet.payments.application.port.out.IdempotencyStore;

@Repository
public class JdbcIdempotencyStore implements IdempotencyStore {

    private final JdbcClient jdbc;

    public JdbcIdempotencyStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public Begin begin(UUID key, String requestHash) {
        var freshId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO idempotency_keys (key, request_hash, order_id, expires_at, created_at)
                VALUES (:key, :hash, :orderId, now() + interval '24 hours', now())
                ON CONFLICT (key) DO NOTHING
                """)
                .param("key", key)
                .param("hash", requestHash)
                .param("orderId", freshId)
                .update();
        var row = jdbc.sql("""
                SELECT request_hash, order_id, response_status, response_body
                FROM idempotency_keys
                WHERE key = :key
                FOR UPDATE
                """)
                .param("key", key)
                .query((rs, ignored) -> new Held(
                        rs.getString("request_hash"),
                        rs.getObject("order_id", UUID.class),
                        rs.getObject("response_status", Integer.class),
                        rs.getString("response_body")))
                .single();
        if (!row.hash().equals(requestHash)) {
            throw new PaymentProblem(409, "idempotency_conflict",
                    "Idempotency-Key was already used with a different body");
        }
        return new Begin(row.orderId(), row.status(), row.body());
    }

    private record Held(String hash, UUID orderId, Integer status, String body) {
    }

    @Override
    public void finish(UUID key, int status, String body) {
        jdbc.sql("""
                UPDATE idempotency_keys
                SET response_status = :status, response_body = :body
                WHERE key = :key AND response_status IS NULL
                """)
                .param("status", status)
                .param("body", body)
                .param("key", key)
                .update();
    }
}
