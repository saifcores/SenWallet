package sn.senwallet.wallet.application.port.out;

import sn.senwallet.wallet.domain.model.Phone;

public interface SmsPort {

    void send(Phone phone, String text);
}
