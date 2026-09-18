package com.paypus.webhooks;

import com.paypus.outbox.OutboxEvent;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class WebhookDeliveryService {
    private final WebhookEndpointRepository webhookEndpointRepository;
    private final WebhookDeliveryRepository webhookDeliveryRepository;

    public WebhookDeliveryService(
            WebhookEndpointRepository webhookEndpointRepository,
            WebhookDeliveryRepository webhookDeliveryRepository
    ){
        this.webhookEndpointRepository = webhookEndpointRepository;
        this.webhookDeliveryRepository = webhookDeliveryRepository;
    }

    @Transactional
    public void scheduleDeliveriesForEvent(OutboxEvent event){
        List<WebhookEndpoint> activeEndpoints = webhookEndpointRepository.findByTenantIdAndIsActiveTrue(event.getTenant().getId());

        for (WebhookEndpoint endpoint : activeEndpoints) {
            boolean alreadyScheduled = webhookDeliveryRepository.existsByWebhookEndpointIdAndOutboxEventId(endpoint.getId(), event.getId());
            if (alreadyScheduled) {
                continue;
            }

            WebhookDelivery delivery = new WebhookDelivery();
            delivery.setWebhookEndpoint(endpoint);
            delivery.setOutboxEventId(event.getId());
            delivery.setStatus(WebhookDeliveryStatus.PENDING);
            delivery.setAttemptCount(0);
            delivery.setNextAttemptAt(OffsetDateTime.now());
            delivery.setCreatedAt(OffsetDateTime.now());
            delivery.setUpdatedAt(OffsetDateTime.now());

            try {
                webhookDeliveryRepository.save(delivery);
            } catch (DataIntegrityViolationException e){
                // Another concurrent call already scheduled this exact
                // (endpoint, event) pair — the unique index caught it, safe to ignore.
            }
        }
    }
}
