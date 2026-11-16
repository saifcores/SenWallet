package sn.senwallet.simulators.common.hmac;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HmacSignerTest {

    @Test
    void signAndVerifyRoundTrip() {
        HmacSigner signer = new HmacSigner("test-secret");
        HmacSigner.SignedPayload signed = signer.sign("{\"ok\":true}");

        assertThat(signer.verify(
                signed.timestamp(),
                signed.nonce(),
                signed.body(),
                signed.signature())).isTrue();
        assertThat(signer.verify(
                signed.timestamp(),
                signed.nonce(),
                "{\"tampered\":true}",
                signed.signature())).isFalse();
    }
}
