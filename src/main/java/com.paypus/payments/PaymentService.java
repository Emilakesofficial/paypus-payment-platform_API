package com.paypus.payments;

import com.stripe.model.Refund;
import com.stripe.param.RefundCreateParams;
import com.paypus.ledger.AccountRepository;
import com.paypus.ledger.AccountType;
import com.paypus.ledger.LedgerEntryRequest;
import com.paypus.ledger.LedgerService;
import com.paypus.ledger.LedgerTransaction;
import com.paypus.ledger.ReferenceType;
import java.util.List;
import com.paypus.tenant.Tenant;
import com.paypus.tenant.TenantRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final TenantRepository tenantRepository;
    private final AccountRepository accountRepository;
    private final LedgerService ledgerService;

    public PaymentService(PaymentRepository paymentRepository, TenantRepository tenantRepository,  AccountRepository accountRepository, LedgerService ledgerService) {
        this.paymentRepository = paymentRepository;
        this.tenantRepository = tenantRepository;
        this.accountRepository = accountRepository;
        this.ledgerService = ledgerService;
    }

    @Transactional
    public PaymentCreationResult createPayment(UUID tenantId, CreatePaymentRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        long amountInCents = request.amount()
                .setScale(2, RoundingMode.UNNECESSARY)
                .multiply(new BigDecimal(100))
                .longValueExact();

        PaymentIntent stripePaymentIntent;
        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency(request.currency().toLowerCase())
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                                    .build()
                    ).build();

            stripePaymentIntent = PaymentIntent.create(params);
        } catch (StripeException e) {
            throw new PaymentProcessingException("Failed to create Stripe PaymentIntent", e);
        }

        Payment payment = new Payment();
        payment.setTenant(tenant);
        payment.setStripePaymentIntentId(stripePaymentIntent.getId());
        payment.setAmount(request.amount());
        payment.setCurrency(request.currency().toLowerCase());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCreatedAt(OffsetDateTime.now());
        payment.setUpdatedAt(OffsetDateTime.now());

        payment = paymentRepository.save(payment);

        return new PaymentCreationResult(payment,stripePaymentIntent.getClientSecret());
    }

    @Transactional
    public RefundResult refundPayment(UUID tenantId, UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        if (!payment.getTenant().getId().equals(tenantId)) {
            throw new IllegalArgumentException("Payment does not belong to this tenant: " + paymentId);
        }

        if (payment.getStatus() != PaymentStatus.CAPTURED){
            throw new IllegalStateException(
                    "Only CAPTURED payments can be refunded, current status: "+ payment.getStatus()
            );
        }

        Refund stripeRefund;
        try {
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(payment.getStripePaymentIntentId())
                    .build();
            stripeRefund = Refund.create(params);
        }catch (StripeException e) {
            throw new PaymentProcessingException("Failed to create Stripe refund", e);
        }

        if (!"succeeded".equals(stripeRefund.getStatus())){
            throw new PaymentProcessingException(
                    "Stripe refund did not succeed immediately, status: " + stripeRefund.getStatus(), null
            );
        }

        UUID merchantBalanceAccountId = findAccount(tenantId, AccountType.MERCHANT_BALANCE, payment.getCurrency());
        UUID stripeClearingAccountId = findAccount(tenantId, AccountType.STRIPE_CLEARING, payment.getCurrency());

        List<LedgerEntryRequest> entries = List.of(
                new LedgerEntryRequest(merchantBalanceAccountId, payment.getAmount().negate()),
                new LedgerEntryRequest(stripeClearingAccountId, payment.getAmount())
        );

        LedgerTransaction transaction = ledgerService.post(
                tenantId,
                ReferenceType.REFUND,
                payment.getId(),
                "Refund for payment " + payment.getId(),
                payment.getCurrency(),
                entries
        );

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setUpdatedAt(OffsetDateTime.now());
        payment = paymentRepository.save(payment);

        return new RefundResult(payment, stripeRefund.getId());
    }

    private  UUID findAccount(UUID tenantId, AccountType accountType, String currency) {
        return accountRepository.findByTenantId(tenantId).stream()
                .filter(a -> a.getAccountType() == accountType && a.getCurrency().equals(currency))
                .findFirst()
                .map(a -> a.getId())
                .orElseThrow(()-> new IllegalStateException(
                        "No " + accountType + " account found for tenant: " + tenantId
        ));
    }
}