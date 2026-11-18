package sn.senwallet.payments.application.service;

import sn.senwallet.payments.domain.model.PaymentOrder;

/** Canonical JSON stored with the idempotency key so a replay is the same body. */
public final class PaymentBodies {

    private PaymentBodies() {
    }

    public static String order(PaymentOrder order) {
        return "{"
                + "\"id\":\"" + order.id() + "\","
                + "\"type\":\"" + label(order.type()) + "\","
                + "\"state\":\"" + label(order.state()) + "\","
                + "\"wallet_id\":" + uuid(order.walletId()) + ","
                + "\"amount\":" + order.amount() + ","
                + "\"fee\":" + order.fee() + ","
                + "\"currency\":\"" + order.currency() + "\","
                + "\"provider\":\"" + label(order.provider()) + "\","
                + "\"provider_ref\":" + text(order.providerRef()) + ","
                + "\"ledger_txn_id\":" + uuid(order.ledgerTxnId()) + ","
                + "\"attempts\":" + order.attempts()
                + "}";
    }

    public static String problem(String code, String detail, PaymentOrder order) {
        var body = "{"
                + "\"type\":\"urn:senwallet:problem:" + code + "\","
                + "\"title\":\"" + code.replace('_', ' ') + "\","
                + "\"status\":422,"
                + "\"detail\":\"" + escape(detail) + "\","
                + "\"code\":\"" + code + "\"";
        if (order != null) {
            body += ",\"order_id\":\"" + order.id() + "\"";
        }
        return body + "}";
    }

    private static String label(Enum<?> value) {
        return value.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String uuid(java.util.UUID value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private static String text(String value) {
        return value == null ? "null" : "\"" + escape(value) + "\"";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
