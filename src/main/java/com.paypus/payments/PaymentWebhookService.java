package com.paypus.payments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paypus.ledger.*;
import com.paypus.outbox.EventType;
import com.paypus.outbox.OutboxEvent;
import com.paypus.outbox.OutboxEventRepository;
import com.paypus.outbox.PaymentCapturedPayload;
import com.paypus.tenant.Tenant;
import com.paypus.tenant.TenantRepository;
import com.paypus.webhooks.WebhookDeliveryService;
import com.stripe.model.PaymentIntent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentWebhookService {
    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final WebhookDeliveryService webhookDeliveryService;
    private final ObjectMapper objectMapper;

    public PaymentWebhookService(
            PaymentRepository paymentRepository,
            OutboxEventRepository outboxEventRepository,
            WebhookDeliveryService webhookDeliveryService,
            ObjectMapper objectMapper
    ){
        this.paymentRepository = paymentRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.webhookDeliveryService = webhookDeliveryService;
        this.objectMapper = objectMapper;
    }
    @Transactional
    public void handlePaymentSucceeded(PaymentIntent stripePaymentIntent) {
        Payment payment = paymentRepository.findByStripePaymentIntentId(stripePaymentIntent.getId())
                .orElseThrow(() -> new IllegalStateException(
                    "No payment found for Stripe PaymentIntent: " + stripePaymentIntent.getId()
                ));

        if (payment.getStatus() == PaymentStatus.CAPTURED){
            return;
        }

        PaymentCapturedPayload payload = new PaymentCapturedPayload(
                payment.getId(),
                payment.getTenant().getId(),
                payment.getAmount(),
                payment.getCurrency()
        );

        String serializedPayload;
        try {
            serializedPayload = objectMapper.writeValueAsString(payload);
        }catch (Exception e){
            throw new IllegalStateException("Failed to serialize PaymentCapturePayload", e);
        }

        OutboxEvent event = new OutboxEvent();
        event.setTenant(payment.getTenant());
        event.setEventType(EventType.PAYMENT_CAPTURED);
        event.setPayload(serializedPayload);
        event.setCreatedAt(OffsetDateTime.now());
        outboxEventRepository.save(event);

        webhookDeliveryService.scheduleDeliveriesForEvent(event);

        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setUpdatedAt(OffsetDateTime.now());
        paymentRepository.save(payment);
    }
}
