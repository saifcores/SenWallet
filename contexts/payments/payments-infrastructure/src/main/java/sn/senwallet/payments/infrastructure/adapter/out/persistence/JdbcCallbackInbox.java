package sn.senwallet.payments.infrastructure.adapter.out.persistence;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import sn.senwallet.payments.application.port.out.CallbackInbox;

@Repository
public class JdbcCallbackInbox implements CallbackInbox {

    private final JdbcClient jdbc;

    public JdbcCallbackInbox(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<StoredCallback> lockPending(int limit) {
        return jdbc.sql("""
                SELECT id, provider, provider_ref, external_ref, status,
                       (body->>'amount')::bigint AS amount,
                       COALESCE(body->>'creditor_alias', body->>'creditorAlias') AS creditor_alias
                FROM callback_inbox
                WHERE processed_at IS NULL
                ORDER BY id
                FOR UPDATE SKIP LOCKED
                LIMIT :limit
                """)
                .param("limit", limit)
                .query((rs, row) -> new StoredCallback(
                        rs.getLong("id"),
                        rs.getString("provider"),
                        rs.getString("provider_ref"),
                        rs.getString("external_ref"),
                        rs.getString("status"),
                        rs.getLong("amount"),
                        rs.getString("creditor_alias")))
                .list();
    }

    @Override
    public void markProcessed(List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        jdbc.sql("UPDATE callback_inbox SET processed_at = now() WHERE id IN (:ids)")
                .param("ids", ids)
                .update();
    }
}
