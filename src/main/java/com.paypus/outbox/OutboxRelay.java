package com.paypus.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelay(OutboxEventRepository outboxEventRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void relayUnpublishedEvents() {
        List<OutboxEvent> unpublished = outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc();

        for (OutboxEvent event : unpublished) {
            String key = event.getTenant().getId().toString();

            try {
                kafkaTemplate.send(KafkaTopics.PAYMENT_EVENTS, key, event.getPayload())
                        .get(5, java.util.concurrent.TimeUnit.SECONDS);

                event.setPublishedAt(OffsetDateTime.now());
                outboxEventRepository.save(event);
            } catch (Exception e) {
                log.warn("Failed to publish outbox event {}, will retry on next poll", event.getId(), e);
            }
        }
    }
}