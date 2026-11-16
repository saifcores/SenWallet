package sn.senwallet.simulators.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;
import sn.senwallet.simulators.common.callback.CallbackDispatcher;
import sn.senwallet.simulators.common.fault.FaultQueue;
import sn.senwallet.simulators.common.hmac.HmacSigner;
import sn.senwallet.simulators.common.web.FaultController;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@Import(FaultController.class)
public class SimulatorCommonConfiguration {

    @Bean
    @ConditionalOnMissingBean
    JsonMapper jsonMapper() {
        return JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    FaultQueue faultQueue() {
        return new FaultQueue();
    }

    @Bean
    @ConditionalOnMissingBean
    HmacSigner hmacSigner(
            @Value("${simulator.hmac-secret:sim-hmac-secret}") String secret) {
        return new HmacSigner(secret);
    }

    @Bean
    @ConditionalOnMissingBean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    CallbackDispatcher callbackDispatcher(
            RestClient.Builder restClientBuilder,
            HmacSigner hmacSigner,
            @Value("${simulator.callback-base-url:}") String callbackBaseUrl) {
        return new CallbackDispatcher(restClientBuilder, hmacSigner, callbackBaseUrl);
    }
}
