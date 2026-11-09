package sn.senwallet.wallet.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sn.senwallet.shared.id.AccountId;
import sn.senwallet.shared.id.CustomerId;
import sn.senwallet.shared.id.LedgerId;
import sn.senwallet.shared.id.WalletId;
import sn.senwallet.shared.money.Money;
import sn.senwallet.wallet.application.exception.WalletProblem;
import sn.senwallet.wallet.application.model.SendResult;
import sn.senwallet.wallet.application.port.out.LedgerGateway;
import sn.senwallet.wallet.application.port.out.LedgerGateway.LedgerLine;
import sn.senwallet.wallet.application.port.out.LedgerGateway.LedgerTransaction;
import sn.senwallet.wallet.application.port.out.LedgerGateway.PlatformAccounts;
import sn.senwallet.wallet.application.port.out.LedgerGateway.PostResult;
import sn.senwallet.wallet.application.port.out.PricingCatalog;
import sn.senwallet.wallet.application.port.out.TransferIdempotency;
import sn.senwallet.wallet.domain.model.Customer;
import sn.senwallet.wallet.domain.model.CustomerStatus;
import sn.senwallet.wallet.domain.model.FeeSchedule;
import sn.senwallet.wallet.domain.model.KycTier;
import sn.senwallet.wallet.domain.model.LimitPolicy;
import sn.senwallet.wallet.domain.model.P2pPosting;
import sn.senwallet.wallet.domain.model.Phone;
import sn.senwallet.wallet.domain.model.TransferId;
import sn.senwallet.wallet.domain.model.Wallet;
import sn.senwallet.wallet.domain.repository.CustomerRepository;
import sn.senwallet.wallet.domain.repository.WalletRepository;

class WalletApplicationTest {

    private final MemoryCustomers customers = new MemoryCustomers();
    private final MemoryWallets wallets = new MemoryWallets();
    private final MemoryIdempotency idempotency = new MemoryIdempotency();
    private final RecordingLedger ledger = new RecordingLedger();
    private final VerifyCustomerHandler verify = new VerifyCustomerHandler(customers, wallets,
            (customer, wallet) -> {
                customers.update(customer);
                wallets.insert(wallet);
            }, ledger);
    private final SendTransferHandler send = new SendTransferHandler(idempotency, wallets, customers,
            new FixedPricing(), ledger, Clock.fixed(Instant.parse("2026-11-05T00:00:00Z"), ZoneOffset.UTC));

    private Wallet sender;
    private Wallet recipient;

    @BeforeEach
    void wallets() {
        sender = open("+221770000001");
        recipient = open("+221770000002");
    }

    @Test
    void replayReturnsTheSameTransferAndDoesNotPostAgain() {
        var key = UUID.randomUUID();

        var first = send.send(key, sender.id(), recipient.alias().value(), 50_000, "XOF");
        var second = send.send(key, sender.id(), recipient.alias().value(), 50_000, "XOF");

        assertThat(first).isInstanceOf(SendResult.Accepted.class);
        assertThat(second.transfer().id()).isEqualTo(first.transfer().id());
        assertThat(ledger.posts).isEqualTo(1);
    }

    @Test
    void aDifferentBodyForTheSameKeyIsAConflict() {
        var key = UUID.randomUUID();
        send.send(key, sender.id(), recipient.alias().value(), 50_000, "XOF");

        assertThatThrownBy(() -> send.send(key, sender.id(), recipient.alias().value(), 1, "XOF"))
                .isInstanceOf(WalletProblem.class)
                .extracting(problem -> ((WalletProblem) problem).status())
                .isEqualTo(409);
        assertThat(ledger.posts).isEqualTo(1);
    }

    @Test
    void aCompletedKeyDoesNotCallTheLedger() {
        var key = UUID.randomUUID();
        send.send(key, sender.id(), recipient.alias().value(), 50_000, "XOF");
        ledger.posts = 0;

        send.send(key, sender.id(), recipient.alias().value(), 50_000, "XOF");

        assertThat(ledger.posts).isZero();
    }

    @Test
    void ledgerFailureLeavesTheCustomerUnverified() {
        var registered = Customer.register(CustomerId.newId(), new Phone("+221770000099"), "123456");
        customers.insert(registered);
        ledger.failOpen = true;

        assertThatThrownBy(() -> verify.verify(registered.id(), "123456"))
                .isInstanceOf(WalletProblem.class);
        assertThat(customers.findById(registered.id()).orElseThrow().status()).isEqualTo(CustomerStatus.UNVERIFIED);
        assertThat(wallets.findByCustomer(registered.id())).isEmpty();
    }

    private Wallet open(String phone) {
        var customer = Customer.register(CustomerId.newId(), new Phone(phone), "123456").verify("123456");
        customers.insert(customer);
        var wallet = Wallet.active(WalletId.newId(), customer.id(), AccountId.newId(), customer.phone());
        wallets.insert(wallet);
        return wallet;
    }

    private static final class FixedPricing implements PricingCatalog {

        @Override
        public LimitPolicy limit(KycTier tier, Instant at) {
            return new LimitPolicy(tier, Money.xof(4_000_000), Instant.parse("2026-11-02T00:00:00Z"));
        }

        @Override
        public FeeSchedule p2pFee(Instant at) {
            return new FeeSchedule(new BigDecimal("0.01"), Instant.parse("2026-11-02T00:00:00Z"));
        }
    }

    private static final class RecordingLedger implements LedgerGateway {

        private int posts;
        private boolean failOpen;

        @Override
        public PlatformAccounts ensurePlatform() {
            return new PlatformAccounts(LedgerId.newId(), AccountId.newId());
        }

        @Override
        public AccountId openWalletAccount(LedgerId ledgerId, String name) {
            if (failOpen) {
                throw new WalletProblem(503, "ledger_unavailable", "ledger is unavailable");
            }
            return AccountId.newId();
        }

        @Override
        public PostResult post(P2pPosting posting) {
            posts++;
            return PostResult.posted(UUID.randomUUID());
        }

        @Override
        public LedgerTransaction transaction(UUID transactionId) {
            return new LedgerTransaction(transactionId, "p2p:test", List.<LedgerLine>of());
        }

        @Override
        public Balances balances(AccountId accountId) {
            return new Balances(0, 0, 0);
        }
    }

    private static final class MemoryCustomers implements CustomerRepository {

        private final Map<UUID, Customer> byId = new HashMap<>();

        @Override
        public void insert(Customer customer) {
            byId.put(customer.id().value(), customer);
        }

        @Override
        public void update(Customer customer) {
            byId.put(customer.id().value(), customer);
        }

        @Override
        public Optional<Customer> findById(CustomerId id) {
            return Optional.ofNullable(byId.get(id.value()));
        }

        @Override
        public Optional<Customer> findByPhone(Phone phone) {
            return byId.values().stream().filter(customer -> customer.phone().equals(phone)).findFirst();
        }
    }

    private static final class MemoryWallets implements WalletRepository {

        private final Map<UUID, Wallet> byId = new HashMap<>();

        @Override
        public void insert(Wallet wallet) {
            byId.put(wallet.id().value(), wallet);
        }

        @Override
        public Optional<Wallet> findById(WalletId id) {
            return Optional.ofNullable(byId.get(id.value()));
        }

        @Override
        public Optional<Wallet> findByAlias(Phone alias) {
            return byId.values().stream().filter(wallet -> wallet.alias().equals(alias)).findFirst();
        }

        @Override
        public Optional<Wallet> findByCustomer(CustomerId customerId) {
            return byId.values().stream().filter(wallet -> wallet.customerId().equals(customerId)).findFirst();
        }

        @Override
        public Optional<Wallet> findByLedgerAccount(UUID ledgerAccountId) {
            return byId.values().stream()
                    .filter(wallet -> wallet.ledgerAccountId().value().equals(ledgerAccountId))
                    .findFirst();
        }
    }

    private static final class MemoryIdempotency implements TransferIdempotency {

        private final Map<UUID, Row> rows = new HashMap<>();

        @Override
        public Begin begin(UUID key, String requestHash) {
            var row = rows.get(key);
            if (row == null) {
                row = new Row(TransferId.newId(), requestHash, null);
                rows.put(key, row);
            } else if (!row.hash.equals(requestHash)) {
                throw new WalletProblem(409, "idempotency_conflict", "different body");
            }
            return new Begin(row.transferId, row.result);
        }

        @Override
        public void finish(UUID key, SendResult result) {
            var row = rows.get(key);
            row.result = result;
        }

        private static final class Row {
            private final TransferId transferId;
            private final String hash;
            private SendResult result;

            private Row(TransferId transferId, String hash, SendResult result) {
                this.transferId = transferId;
                this.hash = hash;
                this.result = result;
            }
        }
    }
}
