package sn.senwallet.payments.infrastructure.adapter.in.schedule;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sn.senwallet.payments.application.port.out.InquirySchedule;

@Component
public class ConfiguredInquirySchedule implements InquirySchedule {

    private final List<Duration> delays;

    public ConfiguredInquirySchedule(
            @Value("${payments.inquiry.delays:PT30S,PT1M,PT2M,PT5M,PT10M}") String delays) {
        this.delays = Arrays.stream(delays.split(",")).map(String::trim).filter(part -> !part.isEmpty())
                .map(Duration::parse).toList();
        if (this.delays.isEmpty()) {
            throw new IllegalArgumentException("at least one inquiry delay is required");
        }
    }

    @Override
    public List<Duration> delays() {
        return delays;
    }
}
