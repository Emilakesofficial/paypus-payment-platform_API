package com.paypus.webhooks;

import com.paypus.AbstractIntegrationTest;
import com.paypus.outbox.EventType;
import com.paypus.outbox.OutboxEvent;
import com.paypus.outbox.OutboxEventRepository;
import com.paypus.tenant.Tenant;
import com.paypus.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
@Import(WebhookDeliveryAttemptServiceIntegrationTest.FakeMerchantEndpoint.class)
class WebhookDeliveryAttemptServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebhookDeliveryAttemptService webhookDeliveryAttemptService;

    @Autowired
    private WebhookEndpointService webhookEndpointService;

    @Autowired
    private WebhookDeliveryRepository webhookDeliveryRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @LocalServerPort
    private int localServerPort;

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setName("Delivery Attempt Test Merchant");
        tenant.setCreatedAt(OffsetDateTime.now());
        tenant = tenantRepository.save(tenant);

        FakeMerchantEndpoint.responseStatus.set(200);
        FakeMerchantEndpoint.receivedSignatureHeader.set(null);
    }

    @Test
    void attemptDelivery_successfulResponse_marksDelivered() {
        FakeMerchantEndpoint.responseStatus.set(200);

        WebhookDelivery delivery = createPendingDelivery();

        webhookDeliveryAttemptService.attemptDelivery(delivery.getId());

        WebhookDelivery updated = webhookDeliveryRepository.findById(delivery.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(WebhookDeliveryStatus.DELIVERED);
        assertThat(updated.getAttemptCount()).isEqualTo(1);
        assertThat(updated.getLastResponseStatus()).isEqualTo(200);
        assertThat(FakeMerchantEndpoint.receivedSignatureHeader.get()).startsWith("t=");
        assertThat(FakeMerchantEndpoint.receivedSignatureHeader.get()).contains("v1=");
    }

    @Test
    void attemptDelivery_failedResponse_schedulesRetryWithBackoff() {
        FakeMerchantEndpoint.responseStatus.set(500);

        WebhookDelivery delivery = createPendingDelivery();

        webhookDeliveryAttemptService.attemptDelivery(delivery.getId());

        WebhookDelivery updated = webhookDeliveryRepository.findById(delivery.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(updated.getAttemptCount()).isEqualTo(1);
        assertThat(updated.getLastResponseStatus()).isEqualTo(500);
        assertThat(updated.getNextAttemptAt()).isAfter(OffsetDateTime.now());
    }

    @Test
    void attemptDelivery_failsRepeatedly_deadLettersAfterMaxAttempts() {
        FakeMerchantEndpoint.responseStatus.set(500);

        WebhookDelivery delivery = createPendingDelivery();

        for (int i = 0; i < 5; i++) {
            webhookDeliveryAttemptService.attemptDelivery(delivery.getId());
        }

        WebhookDelivery updated = webhookDeliveryRepository.findById(delivery.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(WebhookDeliveryStatus.DEAD_LETTERED);
        assertThat(updated.getAttemptCount()).isEqualTo(5);
    }

    private WebhookDelivery createPendingDelivery() {
        WebhookEndpoint endpoint = webhookEndpointService.registerEndpoint(
                tenant.getId(), "http://localhost:" + localServerPort + "/test/fake-merchant-endpoint"
        );

        OutboxEvent event = new OutboxEvent();
        event.setTenant(tenant);
        event.setEventType(EventType.PAYMENT_CAPTURED);
        event.setPayload("{\"paymentId\":\"" + UUID.randomUUID() + "\"}");
        event.setCreatedAt(OffsetDateTime.now());
        event = outboxEventRepository.save(event);

        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setWebhookEndpoint(endpoint);
        delivery.setOutboxEventId(event.getId());
        delivery.setStatus(WebhookDeliveryStatus.PENDING);
        delivery.setAttemptCount(0);
        delivery.setNextAttemptAt(OffsetDateTime.now());
        delivery.setCreatedAt(OffsetDateTime.now());
        delivery.setUpdatedAt(OffsetDateTime.now());

        return webhookDeliveryRepository.save(delivery);
    }

    @RestController
    static class FakeMerchantEndpoint {

        static final AtomicInteger responseStatus = new AtomicInteger(200);
        static final java.util.concurrent.atomic.AtomicReference<String> receivedSignatureHeader =
                new java.util.concurrent.atomic.AtomicReference<>();

        @PostMapping("/test/fake-merchant-endpoint")
        public ResponseEntity<String> receive(
                @org.springframework.web.bind.annotation.RequestHeader("Paypus-Signature") String signature
        ) {
            receivedSignatureHeader.set(signature);
            return ResponseEntity.status(HttpStatus.valueOf(responseStatus.get())).body("received");
        }
    }
}