package sn.senwallet.payments.infrastructure.adapter.out.rail;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import sn.senwallet.payments.application.port.out.RailPort;
import sn.senwallet.payments.domain.model.PaymentOrder;
import sn.senwallet.payments.domain.model.RailStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Calls the M6 simulators. A timeout is unknown, never a retry and never a failure. */
@Component
public class HttpRailGateway implements RailPort {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient wave;
    private final RestClient orange;
    private final RestClient pispi;

    public HttpRailGateway(
            @Value("${payments.rails.wave-url:http://localhost:9102}") String waveUrl,
            @Value("${payments.rails.orange-url:http://localhost:9103}") String orangeUrl,
            @Value("${payments.rails.pispi-url:http://localhost:9101}") String pispiUrl,
            @Value("${payments.rails.timeout:10s}") Duration timeout) {
        this.wave = client(waveUrl, timeout);
        this.orange = client(orangeUrl, timeout);
        this.pispi = client(pispiUrl, timeout);
    }

    @Override
    public RailReply disburse(PaymentOrder.Provider provider, String orderId, String phone, long amount) {
        return post(mno(provider), "/disbursements", money(orderId, phone, amount));
    }

    @Override
    public RailReply collect(PaymentOrder.Provider provider, String orderId, String phone, long amount) {
        return post(mno(provider), "/collections", money(orderId, phone, amount));
    }

    @Override
    public RailReply send(String orderId, String debtorAlias, String creditorAlias, long amount) {
        return post(pispi, "/transfers", """
                {"end_to_end_id":"%s","debtor_alias":"%s","creditor_alias":"%s","amount":%d,"currency":"XOF"}
                """.formatted(orderId, debtorAlias, creditorAlias, amount));
    }

    @Override
    public RailStatus poll(PaymentOrder.Provider provider, String providerRef) {
        var http = provider == PaymentOrder.Provider.PISPI ? pispi : mno(provider);
        var path = provider == PaymentOrder.Provider.PISPI
                ? "/transfers/" + providerRef
                : "/transactions/" + providerRef;
        try {
            var raw = http.get().uri(path).retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new RestClientException("status " + response.getStatusCode().value());
                    })
                    .body(String.class);
            return RailStatus.parse(JSON.readTree(raw).path("status").asString());
        } catch (RestClientException failure) {
            return RailStatus.UNKNOWN;
        }
    }

    private RailReply post(RestClient http, String path, String body) {
        try {
            var raw = http.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(status -> status.value() == 422, (request, response) -> {
                        throw new RailRejected(new String(response.getBody().readAllBytes()));
                    })
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new RestClientException("rail status " + response.getStatusCode().value());
                    })
                    .body(String.class);
            return accepted(JSON.readTree(raw == null ? "{}" : raw));
        } catch (RailRejected rejected) {
            return RailReply.rejected(rejected.getMessage());
        } catch (ResourceAccessException timeout) {
            return RailReply.timeout();
        } catch (RestClientException failure) {
            if (failure.getCause() instanceof ResourceAccessException) {
                return RailReply.timeout();
            }
            return RailReply.timeout();
        }
    }

    private static RailReply accepted(JsonNode tree) {
        var idNode = tree.path("id");
        var id = idNode.isMissingNode() || idNode.isNull() ? null : idNode.asString();
        var status = RailStatus.parse(tree.path("status").asString());
        return new RailReply(status, id, false, null);
    }

    private RestClient mno(PaymentOrder.Provider provider) {
        return provider == PaymentOrder.Provider.ORANGE ? orange : wave;
    }

    private static String money(String orderId, String phone, long amount) {
        return """
                {"external_id":"%s","phone":"%s","amount":%d,"currency":"XOF"}
                """.formatted(orderId, phone, amount);
    }

    private static RestClient client(String baseUrl, Duration timeout) {
        var requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(timeout);
        requests.setReadTimeout(timeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requests).build();
    }

    private static final class RailRejected extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private RailRejected(String detail) {
            super(detail == null || detail.isBlank() ? "rail rejected the order" : detail);
        }
    }
}
