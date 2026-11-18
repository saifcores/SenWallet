package sn.senwallet.payments.infrastructure.adapter.in.messaging;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sn.senwallet.events.WalletEventTypes;
import sn.senwallet.events.inbox.IdempotentInbox;
import sn.senwallet.payments.application.port.out.LedgerPort;
import sn.senwallet.payments.application.port.out.WalletDirectory;
import sn.senwallet.payments.application.port.out.WalletDirectory.ProjectedWallet;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class WalletProjectionListener {

    public static final String QUEUE = "payments.wallet";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final IdempotentInbox inbox;
    private final WalletDirectory wallets;
    private final LedgerPort ledger;
    private final TransactionTemplate transactions;

    public WalletProjectionListener(
            JdbcClient jdbc,
            WalletDirectory wallets,
            LedgerPort ledger,
            PlatformTransactionManager transactions) {
        this.inbox = new IdempotentInbox(jdbc);
        this.wallets = wallets;
        this.ledger = ledger;
        this.transactions = new TransactionTemplate(transactions);
    }

    @RabbitListener(queues = QUEUE)
    public void onEvent(Message message) {
        var tree = JSON.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
        var eventId = UUID.fromString(tree.path("event_id").asString());
        var type = tree.path("event_type").asString();
        transactions.executeWithoutResult(status -> inbox.claim(eventId, () -> apply(type, tree.path("payload"))));
    }

    private void apply(String type, JsonNode payload) {
        if (WalletEventTypes.CREATED.equals(type)) {
            var wallet = new ProjectedWallet(
                    uuid(payload, "wallet_id"),
                    uuid(payload, "customer_id"),
                    uuid(payload, "ledger_id"),
                    uuid(payload, "ledger_account_id"),
                    payload.path("pispi_alias").asString(),
                    payload.path("phone").asString(),
                    payload.path("tier").asString(),
                    payload.path("holding_cap").asLong(),
                    uuid(payload, "fee_account_id"));
            wallets.upsert(wallet);
            ledger.ensureRails(wallet.ledgerId(), wallet.feeAccountId());
            return;
        }
        if (WalletEventTypes.TIER_CHANGED.equals(type)) {
            var current = wallets.find(uuid(payload, "wallet_id")).orElse(null);
            if (current == null) {
                return;
            }
            wallets.upsert(new ProjectedWallet(
                    current.walletId(),
                    current.customerId(),
                    current.ledgerId(),
                    current.ledgerAccountId(),
                    current.alias(),
                    current.phone(),
                    payload.path("tier").asString(),
                    payload.path("holding_cap").asLong(),
                    current.feeAccountId()));
        }
    }

    private static UUID uuid(JsonNode payload, String field) {
        return UUID.fromString(payload.path(field).asString());
    }
}
