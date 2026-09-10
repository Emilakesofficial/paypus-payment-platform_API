package com.paypus.idempotency;

import com.paypus.AbstractIntegrationTest;
import com.paypus.tenant.Tenant;
import com.paypus.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private TenantRepository tenantRepository;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant();
        tenant.setName("Idempotency Test Merchant");
        tenant.setCreatedAt(OffsetDateTime.now());
        tenant = tenantRepository.save(tenant);
        tenantId = tenant.getId();
    }

    @Test
    void newKey_isNotAReplay() {
        IdempotencyResult result = idempotencyService.begin(tenantId, "key-1", "{\"amount\":100}");

        assertThat(result.isReplay()).isFalse();
        assertThat(result.responseStatus()).isNull();
        assertThat(result.responseBody()).isNull();
    }

    @Test
    void completedKey_isReplayedWithCachedResponse() {
        idempotencyService.begin(tenantId, "key-2", "{\"amount\":100}");
        idempotencyService.complete(tenantId, "key-2", 201, "{\"id\":\"payment-1\"}");

        IdempotencyResult result = idempotencyService.begin(tenantId, "key-2", "{\"amount\":100}");

        assertThat(result.isReplay()).isTrue();
        assertThat(result.responseStatus()).isEqualTo(201);
        assertThat(result.responseBody()).isEqualTo("{\"id\":\"payment-1\"}");
    }

    @Test
    void sameKey_differentPayload_throwsConflict() {
        idempotencyService.begin(tenantId, "key-3", "{\"amount\":100}");
        idempotencyService.complete(tenantId, "key-3", 201, "{\"id\":\"payment-1\"}");

        assertThatThrownBy(() ->
                idempotencyService.begin(tenantId, "key-3", "{\"amount\":999}")
        ).isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void stillInProgress_throwsConflict() {
        idempotencyService.begin(tenantId, "key-4", "{\"amount\":100}");

        assertThatThrownBy(() ->
                idempotencyService.begin(tenantId, "key-4", "{\"amount\":100}")
        ).isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void concurrentBegin_sameKey_onlyOneWins() throws InterruptedException {
        int threadCount = 20;
        String sharedKey = "concurrent-key";
        String requestBody = "{\"amount\":100}";

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    IdempotencyResult result = idempotencyService.begin(tenantId, sharedKey, requestBody);
                    if (!result.isReplay()) {
                        successCount.incrementAndGet();
                    }
                } catch (IdempotencyConflictException e) {
                    conflictCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threadCount - 1);
    }
}