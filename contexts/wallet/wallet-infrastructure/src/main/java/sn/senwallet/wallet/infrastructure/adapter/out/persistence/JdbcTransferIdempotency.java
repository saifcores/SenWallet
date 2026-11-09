package sn.senwallet.wallet.infrastructure.adapter.out.persistence;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import sn.senwallet.wallet.application.exception.WalletProblem;
import sn.senwallet.wallet.application.model.SendResult;
import sn.senwallet.wallet.application.port.out.TransferIdempotency;
import sn.senwallet.wallet.domain.model.TransferId;
import sn.senwallet.wallet.domain.repository.TransferRepository;

@Repository
public class JdbcTransferIdempotency implements TransferIdempotency {

    private final JdbcClient jdbc;
    private final TransferRepository transfers;

    public JdbcTransferIdempotency(JdbcClient jdbc, TransferRepository transfers) {
        this.jdbc = jdbc;
        this.transfers = transfers;
    }

    @Override
    @Transactional
    public Begin begin(UUID key, String requestHash) {
        var freshId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO idempotency_keys (
                    key, request_hash, transfer_id, expires_at, created_at)
                VALUES (:key, :hash, :transferId, now() + interval '24 hours', now())
                ON CONFLICT (key) DO NOTHING
                """)
                .param("key", key)
                .param("hash", requestHash)
                .param("transferId", freshId)
                .update();
        var row = jdbc.sql("""
                SELECT request_hash, transfer_id, response_status, response_code, response_detail
                FROM idempotency_keys
                WHERE key = :key
                FOR UPDATE
                """)
                .param("key", key)
                .query((rs, ignored) -> new Row(
                        rs.getString("request_hash"),
                        rs.getObject("transfer_id", UUID.class),
                        rs.getObject("response_status", Integer.class),
                        rs.getString("response_code"),
                        rs.getString("response_detail")))
                .single();
        if (!row.hash.equals(requestHash)) {
            throw new WalletProblem(409, "idempotency_conflict",
                    "Idempotency-Key was already used with a different body");
        }
        var transferId = TransferId.of(row.transferId);
        if (row.status == null) {
            return new Begin(transferId, null);
        }
        var transfer = transfers.findById(transferId)
                .orElseThrow(() -> new IllegalStateException("idempotency key has no transfer " + transferId));
        SendResult previous = row.status == 201
                ? new SendResult.Accepted(transfer)
                : new SendResult.Refused(transfer, row.status, row.code, row.detail);
        return new Begin(transferId, previous);
    }

    @Override
    @Transactional
    public void finish(UUID key, SendResult result) {
        transfers.insert(result.transfer());
        var code = result instanceof SendResult.Refused refused ? refused.code() : null;
        var detail = result instanceof SendResult.Refused refused ? refused.detail() : null;
        var status = result instanceof SendResult.Refused refused ? refused.status() : 201;
        jdbc.sql("""
                UPDATE idempotency_keys
                SET response_status = :status, response_code = :code, response_detail = :detail
                WHERE key = :key AND response_status IS NULL
                """)
                .param("status", status)
                .param("code", code)
                .param("detail", detail)
                .param("key", key)
                .update();
    }

    private record Row(String hash, UUID transferId, Integer status, String code, String detail) {
    }
}
