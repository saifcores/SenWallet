package sn.senwallet.ledger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Minimal JSON client for the ledger API. */
final class Api {

    record Response(int status, JsonNode body, String raw) {
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final String base;

    Api(int port) {
        this.base = "http://localhost:" + port;
    }

    Response get(String path) {
        return send(HttpRequest.newBuilder(URI.create(base + path)).GET());
    }

    Response post(String path, Object body) {
        return send(HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json(body))));
    }

    Response patch(String path, Object body) {
        return send(HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json(body))));
    }

    static String json(Object body) {
        return body instanceof String text ? text : JSON.writeValueAsString(body);
    }

    private static Response send(HttpRequest.Builder request) {
        try {
            var response = HTTP.send(request.timeout(Duration.ofSeconds(30)).build(),
                    HttpResponse.BodyHandlers.ofString());
            var raw = response.body();
            var body = raw == null || raw.isBlank() ? JSON.missingNode() : JSON.readTree(raw);
            return new Response(response.statusCode(), body, raw);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
