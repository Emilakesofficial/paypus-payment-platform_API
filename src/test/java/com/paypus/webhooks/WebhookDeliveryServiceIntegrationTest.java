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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookDeliveryServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebhookDeliveryService webhookDeliveryService;

    @Autowired
    private WebhookEndpointService webhookEndpointService;

    @Autowired
    private WebhookDeliveryRepository webhookDeliveryRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setName("Delivery Scheduling Test Merchant");
        tenant.setCreatedAt(OffsetDateTime.now());
        tenant = tenantRepository.save(tenant);
    }

    @Test
    void scheduleDeliveriesForEvent_createsOnePendingDeliveryPerActiveEndpoint() {
        webhookEndpointService.registerEndpoint(tenant.getId(), "https://example.com/webhook-a");
        webhookEndpointService.registerEndpoint(tenant.getId(), "https://example.com/webhook-b");

        OutboxEvent event = buildAndSaveOutboxEvent();

        webhookDeliveryService.scheduleDeliveriesForEvent(event);

        List<WebhookDelivery> deliveries = webhookDeliveryRepository.findAll().stream()
                .filter(d -> d.getOutboxEventId().equals(event.getId()))
                .toList();

        assertThat(deliveries).hasSize(2);
        assertThat(deliveries).allMatch(d -> d.getStatus() == WebhookDeliveryStatus.PENDING);
        assertThat(deliveries).allMatch(d -> d.getAttemptCount() == 0);
    }

    @Test
    void scheduleDeliveriesForEvent_calledTwice_doesNotCreateDuplicateDeliveries() {
        webhookEndpointService.registerEndpoint(tenant.getId(), "https://example.com/webhook-a");

        OutboxEvent event = buildAndSaveOutboxEvent();

        webhookDeliveryService.scheduleDeliveriesForEvent(event);
        webhookDeliveryService.scheduleDeliveriesForEvent(event);

        long count = webhookDeliveryRepository.findAll().stream()
                .filter(d -> d.getOutboxEventId().equals(event.getId()))
                .count();

        assertThat(count).isEqualTo(1);
    }

    private OutboxEvent buildAndSaveOutboxEvent() {
        OutboxEvent event = new OutboxEvent();
        event.setTenant(tenant);
        event.setEventType(EventType.PAYMENT_CAPTURED);
        event.setPayload("{\"paymentId\":\"" + UUID.randomUUID() + "\"}");
        event.setCreatedAt(OffsetDateTime.now());
        return outboxEventRepository.save(event);
    }
}