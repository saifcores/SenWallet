package sn.senwallet.ledger.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import sn.senwallet.ledger.domain.model.ledger.Ledger;
import sn.senwallet.ledger.domain.model.ledger.LedgerEnvironment;
import sn.senwallet.ledger.domain.repository.LedgerRepository;
import sn.senwallet.shared.id.LedgerId;

@Repository
public class JdbcLedgerRepository implements LedgerRepository {

    private final JdbcClient jdbc;

    public JdbcLedgerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Ledger ledger) {
        jdbc.sql("""
                INSERT INTO ledgers (id, name, environment, created_at)
                VALUES (:id, :name, :environment, :createdAt)
                """)
                .param("id", ledger.id().value())
                .param("name", ledger.name())
                .param("environment", Sql.label(ledger.environment()))
                .param("createdAt", Sql.timestamp(ledger.createdAt()))
                .update();
    }

    @Override
    public Optional<Ledger> findById(LedgerId id) {
        return jdbc.sql("SELECT id, name, environment, created_at FROM ledgers WHERE id = :id")
                .param("id", id.value())
                .query((rs, row) -> new Ledger(
                        new LedgerId(rs.getObject("id", UUID.class)),
                        rs.getString("name"),
                        Sql.fromLabel(LedgerEnvironment.class, rs.getString("environment")),
                        Sql.instant(rs, "created_at")))
                .optional();
    }
}
