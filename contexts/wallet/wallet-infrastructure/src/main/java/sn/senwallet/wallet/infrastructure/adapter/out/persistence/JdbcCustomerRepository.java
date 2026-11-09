package sn.senwallet.wallet.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import sn.senwallet.shared.id.CustomerId;
import sn.senwallet.wallet.application.exception.WalletProblem;
import sn.senwallet.wallet.domain.model.Customer;
import sn.senwallet.wallet.domain.model.CustomerStatus;
import sn.senwallet.wallet.domain.model.KycTier;
import sn.senwallet.wallet.domain.model.Phone;
import sn.senwallet.wallet.domain.repository.CustomerRepository;

@Repository
public class JdbcCustomerRepository implements CustomerRepository {

    private final JdbcClient jdbc;

    public JdbcCustomerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Customer customer) {
        try {
            jdbc.sql("""
                    INSERT INTO customers (id, phone, status, kyc_tier, otp_code, created_at, updated_at)
                    VALUES (:id, :phone, CAST(:status AS customer_status), CAST(:tier AS kyc_tier),
                            :otp, now(), now())
                    """)
                    .param("id", customer.id().value())
                    .param("phone", customer.phone().value())
                    .param("status", Columns.label(customer.status()))
                    .param("tier", Columns.label(customer.tier()))
                    .param("otp", customer.otp())
                    .update();
        } catch (DuplicateKeyException e) {
            throw new WalletProblem(409, "phone_already_registered", "phone is already registered");
        }
    }

    @Override
    public void update(Customer customer) {
        jdbc.sql("""
                UPDATE customers
                SET status = CAST(:status AS customer_status),
                    kyc_tier = CAST(:tier AS kyc_tier),
                    otp_code = :otp,
                    updated_at = now()
                WHERE id = :id
                """)
                .param("status", Columns.label(customer.status()))
                .param("tier", Columns.label(customer.tier()))
                .param("otp", customer.otp())
                .param("id", customer.id().value())
                .update();
    }

    @Override
    public Optional<Customer> findById(CustomerId id) {
        return find("id = :id", id.value());
    }

    @Override
    public Optional<Customer> findByPhone(Phone phone) {
        return find("phone = :id", phone.value());
    }

    private Optional<Customer> find(String where, Object id) {
        return jdbc.sql("""
                SELECT id, phone, status, kyc_tier, otp_code
                FROM customers WHERE %s
                """.formatted(where))
                .param("id", id)
                .query((rs, row) -> new Customer(
                        new CustomerId(rs.getObject("id", UUID.class)),
                        new Phone(rs.getString("phone")),
                        Columns.enumOf(CustomerStatus.class, rs.getString("status")),
                        Columns.enumOf(KycTier.class, rs.getString("kyc_tier")),
                        rs.getString("otp_code")))
                .optional();
    }
}
