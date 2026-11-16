package sn.senwallet.simulators.common.callback;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import sn.senwallet.simulators.common.hmac.HmacSigner;

/** Posts a signed JSON callback after an optional delay. */
public final class CallbackDispatcher implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CallbackDispatcher.class);

    private final RestClient restClient;
    private final HmacSigner signer;
    private final String callbackBaseUrl;
    private final ExecutorService executor =
            Executors.newVirtualThreadPerTaskExecutor();

    public CallbackDispatcher(
            RestClient.Builder restClientBuilder,
            HmacSigner signer,
            String callbackBaseUrl) {
        this.restClient = restClientBuilder.build();
        this.signer = signer;
        this.callbackBaseUrl = callbackBaseUrl == null ? "" : callbackBaseUrl.trim();
    }

    public boolean isConfigured() {
        return !callbackBaseUrl.isBlank();
    }

    public void dispatchAsync(String path, String jsonBody, long delayMs) {
        if (!isConfigured()) {
            log.debug("callback base URL empty; skipping {}", path);
            return;
        }
        afterDelay(delayMs, () -> post(path, jsonBody));
    }

    /**
     * Runs {@code action} after {@code delayMs} on a virtual thread. Used when
     * the simulator must flip stored status even if callbacks are disabled.
     */
    public void afterDelay(long delayMs, Runnable action) {
        executor.execute(() -> {
            sleep(delayMs);
            action.run();
        });
    }

    public void dispatchNow(String path, String jsonBody) {
        if (!isConfigured()) {
            return;
        }
        post(path, jsonBody);
    }

    private void post(String path, String jsonBody) {
        HmacSigner.SignedPayload signed = signer.sign(jsonBody);
        String url = join(callbackBaseUrl, path);
        try {
            restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HmacSigner.HEADER_TIMESTAMP, signed.timestamp())
                    .header(HmacSigner.HEADER_NONCE, signed.nonce())
                    .header(HmacSigner.HEADER_SIGNATURE, signed.signature())
                    .body(signed.body())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ex) {
            log.warn("callback to {} failed: {}", url, ex.toString());
        }
    }

    private static String join(String base, String path) {
        if (path == null || path.isBlank()) {
            return base;
        }
        if (base.endsWith("/") && path.startsWith("/")) {
            return base.substring(0, base.length() - 1) + path;
        }
        if (!base.endsWith("/") && !path.startsWith("/")) {
            return base + "/" + path;
        }
        return base + path;
    }

    private static void sleep(long delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
