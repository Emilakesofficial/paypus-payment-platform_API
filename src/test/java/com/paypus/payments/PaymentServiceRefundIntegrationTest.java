package com.paypus.payments;
import com.paypus.AbstractIntegrationTest;
import com.paypus.ledger.AccountRepository;
import com.paypus.tenant.Tenant;
import com.paypus.tenant.TenantRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentConfirmParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentServiceRefundIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    AccountRepository accountRepository;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant();
        tenant.setName("Refund Test Merchant");
        tenant.setCreatedAt(OffsetDateTime.now());
        tenant = tenantRepository.save(tenant);
        tenantId = tenant.getId();
    }

    @Test
    void refundPayment_pendingPayment_throwsIllegalStateException() {
        CreatePaymentRequest request = new CreatePaymentRequest(new BigDecimal("20.00"), "usd");
        PaymentCreationResult result = paymentService.createPayment(tenantId, request);

        assertThatThrownBy(() ->
                paymentService.refundPayment(tenantId, result.payment().getId())
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refundPayment_wrongTenant_throwsIllegalArgumentException() {
        CreatePaymentRequest request = new CreatePaymentRequest(new BigDecimal("20.00"), "usd");
        PaymentCreationResult result = paymentService.createPayment(tenantId, request);

        UUID otherTenantId = UUID.randomUUID();

        assertThatThrownBy(() ->
                paymentService.refundPayment(otherTenantId, result.payment().getId())
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refundPayment_capturedPayment_refundsSuccessfullyAndReversesLedger() throws StripeException {
        CreatePaymentRequest request = new CreatePaymentRequest(new BigDecimal("30.00"), "usd");
        PaymentCreationResult result = paymentService.createPayment(tenantId, request);

        String stripePaymentIntentId = result.payment().getStripePaymentIntentId();
        PaymentIntentConfirmParams confirmParams = PaymentIntentConfirmParams.builder()
                .setPaymentMethod("pm_card_visa")
                .build();
        PaymentIntent.retrieve(stripePaymentIntentId).confirm(confirmParams);

        com.paypus.ledger.Account merchantBalance = new com.paypus.ledger.Account();
        merchantBalance.setTenant(tenantRepository.findById(tenantId).orElseThrow());
        merchantBalance.setAccountType(com.paypus.ledger.AccountType.MERCHANT_BALANCE);
        merchantBalance.setCurrency("usd");
        merchantBalance.setCreatedAt(OffsetDateTime.now());
        accountRepository.save(merchantBalance);

        com.paypus.ledger.Account stripeClearing = new com.paypus.ledger.Account();
        stripeClearing.setTenant(tenantRepository.findById(tenantId).orElseThrow());
        stripeClearing.setAccountType(com.paypus.ledger.AccountType.STRIPE_CLEARING);
        stripeClearing.setCurrency("usd");
        stripeClearing.setCreatedAt(OffsetDateTime.now());
        accountRepository.save(stripeClearing);

        Payment payment = paymentRepository.findById(result.payment().getId()).orElseThrow();
        payment.setStatus(PaymentStatus.CAPTURED);
        paymentRepository.save(payment);

        RefundResult refundResult = paymentService.refundPayment(tenantId, result.payment().getId());

        assertThat(refundResult.payment().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(refundResult.stripeRefundId()).startsWith("re_");
    }
}