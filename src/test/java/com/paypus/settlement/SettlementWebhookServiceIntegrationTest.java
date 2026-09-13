package com.paypus.settlement;

import com.paypus.AbstractIntegrationTest;
import com.paypus.tenant.Tenant;
import com.paypus.tenant.TenantRepository;
import com.stripe.model.Payout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementWebhookServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SettlementWebhookService settlementWebhookService;

    @Autowired
    private SettlementBatchRepository settlementBatchRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private SettlementBatch pendingBatch;

    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant();
        tenant.setName("Settlement Webhook Test Merchant");
        tenant.setCreatedAt(OffsetDateTime.now());
        tenant = tenantRepository.save(tenant);

        pendingBatch = new SettlementBatch();
        pendingBatch.setTenant(tenant);
        pendingBatch.setStatus(SettlementStatus.PENDING);
        pendingBatch.setTotalAmount(new BigDecimal("100.00"));
        pendingBatch.setCurrency("usd");
        pendingBatch.setStripePayoutId("po_test_" + UUID.randomUUID());
        pendingBatch.setCreatedAt(OffsetDateTime.now());
        pendingBatch.setUpdatedAt(OffsetDateTime.now());
        pendingBatch = settlementBatchRepository.save(pendingBatch);
    }

    @Test
    void handlePayoutPaid_flipsBatchToPaidOut() {
        Payout payout = new Payout();
        payout.setId(pendingBatch.getStripePayoutId());

        settlementWebhookService.handlePayoutPaid(payout);

        SettlementBatch updated = settlementBatchRepository.findById(pendingBatch.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SettlementStatus.PAID_OUT);
    }

    @Test
    void handlePayoutPaid_calledTwice_doesNotThrowOrChangeAnythingElse() {
        Payout payout = new Payout();
        payout.setId(pendingBatch.getStripePayoutId());

        settlementWebhookService.handlePayoutPaid(payout);
        settlementWebhookService.handlePayoutPaid(payout);

        SettlementBatch updated = settlementBatchRepository.findById(pendingBatch.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SettlementStatus.PAID_OUT);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "no_account",
            "account_closed",
            "insufficient_funds",
            "debit_not_authorized",
            "invalid_currency"
    })
    void handlePayoutFailed_recordsTheSpecificFailureReason(String failureCode) {
        Payout payout = new Payout();
        payout.setId(pendingBatch.getStripePayoutId());
        payout.setFailureCode(failureCode);

        settlementWebhookService.handlePayoutFailed(payout);

        SettlementBatch updated = settlementBatchRepository.findById(pendingBatch.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SettlementStatus.FAILED);
        assertThat(updated.getFailureReason()).isEqualTo(failureCode);
    }

    @Test
    void handlePayoutPaid_unknownPayoutId_throwsIllegalStateException() {
        Payout payout = new Payout();
        payout.setId("po_does_not_exist");

        assertThatThrownBy(() -> settlementWebhookService.handlePayoutPaid(payout))
                .isInstanceOf(IllegalStateException.class);
    }
}