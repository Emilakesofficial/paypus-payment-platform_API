package com.paypus.payments;

import com.paypus.AbstractIntegrationTest;
import com.paypus.outbox.EventType;
import com.paypus.outbox.OutboxEvent;
import com.paypus.outbox.OutboxEventRepository;
import com.paypus.tenant.Tenant;
import com.paypus.tenant.TenantRepository;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentWebhookServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentWebhookService paymentWebhookService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private UUID tenantId;
    private Payment pendingPayment;

    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant();
        tenant.setName("Webhook Test Merchant");
        tenant.setCreatedAt(OffsetDateTime.now());
        tenant = tenantRepository.save(tenant);
        tenantId = tenant.getId();

        pendingPayment = new Payment();
        pendingPayment.setTenant(tenant);
        pendingPayment.setStripePaymentIntentId("pi_test_" + UUID.randomUUID());
        pendingPayment.setAmount(new BigDecimal("75.00"));
        pendingPayment.setCurrency("usd");
        pendingPayment.setStatus(PaymentStatus.PENDING);
        pendingPayment.setCreatedAt(OffsetDateTime.now());
        pendingPayment.setUpdatedAt(OffsetDateTime.now());
        pendingPayment = paymentRepository.save(pendingPayment);
    }

    @Test
    void handlePaymentSucceeded_capturesPaymentAndWritesOutboxEvent() {
        PaymentIntent stripePaymentIntent = new PaymentIntent();
        stripePaymentIntent.setId(pendingPayment.getStripePaymentIntentId());

        paymentWebhookService.handlePaymentSucceeded(stripePaymentIntent);

        Payment updated = paymentRepository.findById(pendingPayment.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(updated.getLedgerTransactionId()).isNull();

        List<OutboxEvent> events = outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc();
        OutboxEvent event = events.stream()
                .filter(e -> e.getTenant().getId().equals(tenantId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No outbox event found for tenant"));

        assertThat(event.getEventType()).isEqualTo(EventType.PAYMENT_CAPTURED);
        assertThat(event.getPayload()).contains(pendingPayment.getId().toString());
        assertThat(event.getPublishedAt()).isNull();
    }

    @Test
    void handlePaymentSucceeded_calledTwice_doesNotWriteASecondOutboxEvent() {
        PaymentIntent stripePaymentIntent = new PaymentIntent();
        stripePaymentIntent.setId(pendingPayment.getStripePaymentIntentId());

        paymentWebhookService.handlePaymentSucceeded(stripePaymentIntent);
        paymentWebhookService.handlePaymentSucceeded(stripePaymentIntent);

        long count = outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc().stream()
                .filter(e -> e.getTenant().getId().equals(tenantId))
                .count();

        assertThat(count).isEqualTo(1);
    }

    @Test
    void handlePaymentSucceeded_unknownPaymentIntent_throwsIllegalStateException() {
        PaymentIntent stripePaymentIntent = new PaymentIntent();
        stripePaymentIntent.setId("pi_does_not_exist");

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                paymentWebhookService.handlePaymentSucceeded(stripePaymentIntent)
        ).isInstanceOf(IllegalStateException.class);
    }
}