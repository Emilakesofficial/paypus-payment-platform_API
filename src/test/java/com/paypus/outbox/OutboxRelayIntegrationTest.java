package com.paypus.outbox;

import com.paypus.AbstractIntegrationTest;
import com.paypus.tenant.Tenant;
import com.paypus.tenant.TenantRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxRelayIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant();
        tenant.setName("Relay Test Merchant");
        tenant.setCreatedAt(OffsetDateTime.now());
        tenant = tenantRepository.save(tenant);
        tenantId = tenant.getId();
    }

    @Test
    void relayUnpublishedEvents_publishesToKafkaAndMarksPublished() {
        OutboxEvent event = new OutboxEvent();
        event.setTenant(tenantRepository.findById(tenantId).orElseThrow());
        event.setEventType(EventType.PAYMENT_CAPTURED);
        event.setPayload("{\"test\":\"payload-" + UUID.randomUUID() + "\"}");
        event.setCreatedAt(OffsetDateTime.now());
        event = outboxEventRepository.save(event);

        String expectedPayload = event.getPayload();
        UUID eventId = event.getId();

        outboxRelay.relayUnpublishedEvents();

        OutboxEvent updated = outboxEventRepository.findById(eventId).orElseThrow();
        assertThat(updated.getPublishedAt()).isNotNull();

        List<String> messages = consumeMessages(KafkaTopics.PAYMENT_EVENTS, 1);
        assertThat(messages).anyMatch(m -> m.equals(expectedPayload));
    }

    private List<String> consumeMessages(String topic, int expectedCount) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));

            List<String> collected = new java.util.ArrayList<>();
            long deadline = System.currentTimeMillis() + 10_000;

            while (collected.size() < expectedCount && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    collected.add(record.value());
                }
            }
            return collected;
        }
    }
}