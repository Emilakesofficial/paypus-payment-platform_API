package com.paypus.webhooks;

import com.paypus.outbox.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class WebhookDeliveryAttemptService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryAttemptService.class);

    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    public WebhookDeliveryAttemptService(
            WebhookDeliveryRepository webhookDeliveryRepository,
            OutboxEventRepository outboxEventRepository
    ) {
        this.webhookDeliveryRepository = webhookDeliveryRepository;
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public void attemptDelivery(UUID deliveryId) {
        WebhookDelivery delivery = webhookDeliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalStateException("Delivery not found: " + deliveryId));

        String payload = outboxEventRepository.findById(delivery.getOutboxEventId())
                .map(event -> event.getPayload())
                .orElse(null);

        if (payload == null) {
            log.warn("Outbox event {} no longer exists, dead-lettering delivery {}",
                    delivery.getOutboxEventId(), delivery.getId());
            delivery.setStatus(WebhookDeliveryStatus.DEAD_LETTERED);
            delivery.setUpdatedAt(OffsetDateTime.now());
            webhookDeliveryRepository.save(delivery);
            return;
        }

        long timestamp = Instant.now().getEpochSecond();
        String signature = HmacSigner.sign(payload, delivery.getWebhookEndpoint().getSecret(), timestamp);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Paypus-Signature", signature);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        HttpEntity<String> requestEntity = new HttpEntity<>(payload, headers);

        int newAttemptCount = delivery.getAttemptCount() + 1;
        delivery.setAttemptCount(newAttemptCount);
        delivery.setLastAttemptAt(OffsetDateTime.now());

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    delivery.getWebhookEndpoint().getUrl(), requestEntity, String.class
            );

            delivery.setLastResponseStatus(response.getStatusCode().value());

            if (response.getStatusCode().is2xxSuccessful()) {
                delivery.setStatus(WebhookDeliveryStatus.DELIVERED);
            } else {
                scheduleRetryOrDeadLetter(delivery, newAttemptCount);
            }
        } catch (HttpStatusCodeException e) {
            log.warn("Webhook delivery {} failed with status {}: {}", delivery.getId(), e.getStatusCode().value(), e.getResponseBodyAsString());
            delivery.setLastResponseStatus(e.getStatusCode().value());
            scheduleRetryOrDeadLetter(delivery, newAttemptCount);
        } catch (RestClientException e) {
            log.warn("Webhook delivery {} failed: {}", delivery.getId(), e.getMessage());
            delivery.setLastResponseStatus(null);
            scheduleRetryOrDeadLetter(delivery, newAttemptCount);
        }

        delivery.setUpdatedAt(OffsetDateTime.now());
        webhookDeliveryRepository.save(delivery);
    }

    private void scheduleRetryOrDeadLetter(WebhookDelivery delivery, int attemptCount) {
        if (RetryBackoff.shouldDeadLetter(attemptCount)) {
            delivery.setStatus(WebhookDeliveryStatus.DEAD_LETTERED);
        } else {
            long delaySeconds = RetryBackoff.nextDelaySeconds(attemptCount);
            delivery.setNextAttemptAt(OffsetDateTime.now().plusSeconds(delaySeconds));
        }
    }
}