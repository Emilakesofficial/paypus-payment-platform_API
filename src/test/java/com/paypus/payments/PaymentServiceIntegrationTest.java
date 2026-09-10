package com.paypus.payments;

import com.paypus.AbstractIntegrationTest;
import com.paypus.tenant.Tenant;
import com.paypus.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant();
        tenant.setName("Payment Test Merchant");
        tenant.setCreatedAt(OffsetDateTime.now());
        tenant = tenantRepository.save(tenant);
        tenantId = tenant.getId();
    }

    @Test
    void createPayment_createsRealStripePaymentIntentAndPendingPayment() {
        CreatePaymentRequest request = new CreatePaymentRequest(new BigDecimal("50.00"), "usd");

        PaymentCreationResult result = paymentService.createPayment(tenantId, request);

        assertThat(result.payment().getId()).isNotNull();
        assertThat(result.payment().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.payment().getAmount()).isEqualByComparingTo("50.00");
        assertThat(result.payment().getCurrency()).isEqualTo("usd");
        assertThat(result.payment().getStripePaymentIntentId()).startsWith("pi_");
        assertThat(result.stripeClientSecret()).contains(result.payment().getStripePaymentIntentId());

        Payment persisted = paymentRepository.findById(result.payment().getId()).orElseThrow();
        assertThat(persisted.getStripePaymentIntentId()).isEqualTo(result.payment().getStripePaymentIntentId());
    }

    @Test
    void createPayment_unknownTenant_throwsIllegalArgumentException() {
        CreatePaymentRequest request = new CreatePaymentRequest(new BigDecimal("50.00"), "usd");
        UUID unknownTenantId = UUID.randomUUID();

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                paymentService.createPayment(unknownTenantId, request)
        ).isInstanceOf(IllegalArgumentException.class);
    }
}