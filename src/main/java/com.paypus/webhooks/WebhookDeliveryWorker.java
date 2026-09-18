package com.paypus.webhooks;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class WebhookDeliveryWorker {

    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final WebhookDeliveryAttemptService webhookDeliveryAttemptService;

    public WebhookDeliveryWorker(
            WebhookDeliveryRepository webhookDeliveryRepository,
            WebhookDeliveryAttemptService webhookDeliveryAttemptService
    ) {
        this.webhookDeliveryRepository = webhookDeliveryRepository;
        this.webhookDeliveryAttemptService = webhookDeliveryAttemptService;
    }

    @Scheduled(fixedDelay = 5000)
    public void deliverDueWebhooks() {
        List<WebhookDelivery> due = webhookDeliveryRepository
                .findByStatusAndNextAttemptAtLessThanEqual(WebhookDeliveryStatus.PENDING, OffsetDateTime.now());

        for (WebhookDelivery delivery : due) {
            webhookDeliveryAttemptService.attemptDelivery(delivery.getId());
        }
    }
}