package sn.senwallet.wallet.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import sn.senwallet.shared.id.WalletId;

/** SHA-256 of the transfer body, length-prefixed so fields cannot run together. */
public final class TransferRequestHasher {

    private TransferRequestHasher() {
    }

    public static String hash(WalletId fromWalletId, String toPhone, long amount, String currency) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            field(digest, fromWalletId.toString());
            field(digest, toPhone);
            field(digest, Long.toString(amount));
            field(digest, currency);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JVM", e);
        }
    }

    private static void field(MessageDigest digest, String value) {
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((bytes.length + ":").getBytes(StandardCharsets.US_ASCII));
        digest.update(bytes);
    }
}
