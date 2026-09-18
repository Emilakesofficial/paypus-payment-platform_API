package com.paypus.webhooks;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {
    List<WebhookDelivery> findByStatusAndNextAttemptAtLessThanEqual(
            WebhookDeliveryStatus status,
            OffsetDateTime cutoff
    );

    boolean existsByWebhookEndpointIdAndOutboxEventId(UUID webhookEndpointId, UUID outboxEventId);
}
