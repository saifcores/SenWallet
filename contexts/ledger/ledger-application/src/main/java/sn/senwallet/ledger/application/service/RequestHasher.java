package sn.senwallet.ledger.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import sn.senwallet.ledger.application.command.PostTransactionCommand;
import sn.senwallet.ledger.application.command.ReverseTransactionCommand;
import sn.senwallet.ledger.domain.model.condition.BalanceCondition;
import sn.senwallet.ledger.domain.model.transaction.Entry;
import sn.senwallet.ledger.domain.model.transaction.RequestHash;

/**
 * SHA-256 of a canonical form of a request. Every field is written as
 * {@code <length>:<value>}, so no value can be confused with a separator;
 * entries keep their order, metadata is sorted by key.
 */
public final class RequestHasher {

    private RequestHasher() {
    }

    public static RequestHash hash(PostTransactionCommand command) {
        var canonical = new Canonical()
                .field("post")
                .field(command.ledgerId().toString())
                .field(command.externalId().value())
                .field(command.status().name());
        entries(canonical, command.entries());
        conditions(canonical, command.conditions());
        canonical.field(command.description() == null ? "" : command.description());
        metadata(canonical, command.metadata());
        return canonical.digest();
    }

    public static RequestHash hash(ReverseTransactionCommand command) {
        var canonical = new Canonical()
                .field("reverse")
                .field(command.transactionId().toString())
                .field(command.effectiveExternalId().value());
        conditions(canonical, command.conditions());
        return canonical.digest();
    }

    private static void entries(Canonical canonical, List<Entry> entries) {
        canonical.field(Integer.toString(entries.size()));
        for (var entry : entries) {
            canonical.field(entry.accountId().toString())
                    .field(entry.direction().name())
                    .field(entry.amount().value().amount().toPlainString())
                    .field(entry.amount().currency().name());
        }
    }

    private static void conditions(Canonical canonical, List<BalanceCondition> conditions) {
        canonical.field(Integer.toString(conditions.size()));
        for (var condition : conditions) {
            canonical.field(condition.accountId().toString())
                    .field(condition.type().name())
                    .field(condition.operator().name())
                    .field(condition.threshold().amount().toPlainString())
                    .field(condition.threshold().currency().name());
        }
    }

    private static void metadata(Canonical canonical, Map<String, String> metadata) {
        var sorted = new TreeMap<>(metadata);
        canonical.field(Integer.toString(sorted.size()));
        sorted.forEach((key, value) -> canonical.field(key).field(value));
    }

    private static final class Canonical {

        private final MessageDigest digest;

        Canonical() {
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 is required by every JVM", e);
            }
        }

        Canonical field(String value) {
            var bytes = value.getBytes(StandardCharsets.UTF_8);
            digest.update((bytes.length + ":").getBytes(StandardCharsets.US_ASCII));
            digest.update(bytes);
            return this;
        }

        RequestHash digest() {
            return RequestHash.of(digest.digest());
        }
    }
}
