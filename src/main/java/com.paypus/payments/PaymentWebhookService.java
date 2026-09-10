package com.paypus.payments;

import com.paypus.ledger.*;
import com.paypus.tenant.Tenant;
import com.paypus.tenant.TenantRepository;
import com.stripe.model.PaymentIntent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentWebhookService {
    private final PaymentRepository  paymentRepository;
    private final AccountRepository accountRepository;
    private final TenantRepository tenantRepository;
    private final LedgerService ledgerService;

    public PaymentWebhookService(
            PaymentRepository paymentRepository,
            AccountRepository accountRepository,
            TenantRepository tenantRepository,
            LedgerService ledgerService
    ){
        this.paymentRepository = paymentRepository;
        this.accountRepository = accountRepository;
        this.tenantRepository = tenantRepository;
        this.ledgerService = ledgerService;
    }
    @Transactional
    public void handlePaymentSucceeded(PaymentIntent stripePaymentIntent) {
        Payment payment = paymentRepository.findByStripePaymentIntentId(stripePaymentIntent.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "No payment found for stripe Payment: " + stripePaymentIntent.getId()
                ));
        if (payment.getStatus() == PaymentStatus.CAPTURED){
            return;
        }
        UUID merchantBalanceAccountId = findOrCreateAccount(
                payment.getTenant().getId(), AccountType.MERCHANT_BALANCE, payment.getCurrency()
        );
        UUID stripeClearingAccountId = findOrCreateAccount(
                payment.getTenant().getId(), AccountType.STRIPE_CLEARING, payment.getCurrency()
        );

        List<LedgerEntryRequest> entries = List.of(
                new LedgerEntryRequest(merchantBalanceAccountId, payment.getAmount()),
                new LedgerEntryRequest(stripeClearingAccountId, payment.getAmount().negate())
        );

        LedgerTransaction transaction = ledgerService.post(
                payment.getTenant().getId(),
                ReferenceType.PAYMENT,
                payment.getId(),
                "Payment captured via Stripe",
                payment.getCurrency(),
                entries
        );

        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setLedgerTransactionId(transaction.getId());
        payment.setUpdatedAt(OffsetDateTime.now());
        paymentRepository.save(payment);
    }

    private UUID findOrCreateAccount(UUID tenantId, AccountType accountType, String currency){
        return accountRepository.findByTenantId(tenantId).stream()
                .filter(a -> a.getAccountType() == accountType && a.getCurrency().equals(currency))
                .findFirst()
                .map(a -> a.getId())
                .orElseGet(() -> createAccount(tenantId, accountType, currency));
    }

    private UUID createAccount(UUID tenantId, AccountType accountType, String currency){
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found for id: " + tenantId));

        Account account = new Account();
        account.setTenant(tenant);
        account.setAccountType(accountType);
        account.setCurrency(currency);
        account.setCreatedAt(OffsetDateTime.now());

        return accountRepository.save(account).getId();
    }
}
