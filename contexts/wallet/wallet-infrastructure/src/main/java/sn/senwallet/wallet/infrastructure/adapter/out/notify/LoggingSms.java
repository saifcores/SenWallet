package sn.senwallet.wallet.infrastructure.adapter.out.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sn.senwallet.wallet.application.port.out.SmsPort;
import sn.senwallet.wallet.domain.model.Phone;

/** Simulated SMS. There is no network; the line is the message. */
@Component
public class LoggingSms implements SmsPort {

    public static final String LOGGER_NAME = "sn.senwallet.wallet.sms";

    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

    @Override
    public void send(Phone phone, String text) {
        log.info("SMS {} {}", phone.value(), text);
    }
}
