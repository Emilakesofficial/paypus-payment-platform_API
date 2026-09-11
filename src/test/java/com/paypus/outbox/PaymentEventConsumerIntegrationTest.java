package com.paypus.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paypus.AbstractIntegrationTest;
import com.paypus.ledger.Account;
import com.paypus.ledger.AccountRepository;
import com.paypus.ledger.AccountType;
import com.paypus.payments.Payment;
import com.paypus.payments.PaymentRepository;
import com.paypus.payments.PaymentStatus;
import com.paypus.tenant.Tenant;
import com.paypus.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentEventConsumerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentEventConsumer paymentEventConsumer;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID tenantId;
    private Payment capturedPayment;

    @BeforeEach
    void setUp() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setName("Consumer Test Merchant");
        tenant.setCreatedAt(OffsetDateTime.now());
        tenant = tenantRepository.save(tenant);
        tenantId = tenant.getId();

        capturedPayment = new Payment();
        capturedPayment.setTenant(tenant);
        capturedPayment.setStripePaymentIntentId("pi_test_" + UUID.randomUUID());
        capturedPayment.setAmount(new BigDecimal("55.00"));
        capturedPayment.setCurrency("usd");
        capturedPayment.setStatus(PaymentStatus.CAPTURED);
        capturedPayment.setCreatedAt(OffsetDateTime.now());
        capturedPayment.setUpdatedAt(OffsetDateTime.now());
        capturedPayment = paymentRepository.save(capturedPayment);
    }

    @Test
    void handlePaymentEvent_postsBalancedLedgerEntriesAndBackfillsTransactionId() throws Exception {
        String message = buildPayloadMessage();

        paymentEventConsumer.handlePaymentEvent(message);

        Payment updated = paymentRepository.findById(capturedPayment.getId()).orElseThrow();
        assertThat(updated.getLedgerTransactionId()).isNotNull();

        List<Account> accounts = accountRepository.findByTenantId(tenantId);
        Account merchantBalance = accounts.stream()
                .filter(a -> a.getAccountType() == AccountType.MERCHANT_BALANCE)
                .findFirst().orElseThrow();
        Account stripeClearing = accounts.stream()
                .filter(a -> a.getAccountType() == AccountType.STRIPE_CLEARING)
                .findFirst().orElseThrow();

        assertThat(accountRepository.computeBalance(merchantBalance.getId())).isEqualByComparingTo("55.00");
        assertThat(accountRepository.computeBalance(stripeClearing.getId())).isEqualByComparingTo("-55.00");
    }

    @Test
    void handlePaymentEvent_processedTwice_doesNotDoublePostLedger() throws Exception {
        String message = buildPayloadMessage();

        paymentEventConsumer.handlePaymentEvent(message);
        paymentEventConsumer.handlePaymentEvent(message);

        List<Account> accounts = accountRepository.findByTenantId(tenantId);
        Account merchantBalance = accounts.stream()
                .filter(a -> a.getAccountType() == AccountType.MERCHANT_BALANCE)
                .findFirst().orElseThrow();

        assertThat(accountRepository.computeBalance(merchantBalance.getId())).isEqualByComparingTo("55.00");
    }

    private String buildPayloadMessage() throws Exception {
        PaymentCapturedPayload payload = new PaymentCapturedPayload(
                capturedPayment.getId(),
                tenantId,
                capturedPayment.getAmount(),
                capturedPayment.getCurrency()
        );
        return objectMapper.writeValueAsString(payload);
    }
}