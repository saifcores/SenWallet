package sn.senwallet.payments.infrastructure.adapter.in.schedule;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.senwallet.payments.application.service.CallbackProcessor;
import sn.senwallet.payments.application.service.InquiryProcessor;

@Component
public class PaymentWorkers {

    private final CallbackProcessor callbacks;
    private final InquiryProcessor inquiries;

    public PaymentWorkers(CallbackProcessor callbacks, InquiryProcessor inquiries) {
        this.callbacks = callbacks;
        this.inquiries = inquiries;
    }

    @Scheduled(fixedDelayString = "${payments.workers.delay:1000}")
    @Transactional
    public void drainCallbacks() {
        callbacks.drain();
    }

    @Scheduled(fixedDelayString = "${payments.workers.delay:1000}")
    @Transactional
    public void drainInquiries() {
        inquiries.drain();
    }
}
