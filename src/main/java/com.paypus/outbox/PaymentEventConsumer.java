package com.paypus.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paypus.ledger.*;
import com.paypus.payments.Payment;
import com.paypus.payments.PaymentRepository;
import com.paypus.tenant.Tenant;
import com.paypus.tenant.TenantRepository;
import org.flywaydb.core.internal.proprietaryStubs.LicensingConfigurationExtensionStub;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.stylesheets.LinkStyle;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class PaymentEventConsumer {
    private final PaymentRepository paymentRepository;
    private final AccountRepository accountRepository;
    private final TenantRepository tenantRepository;
    private final LedgerService ledgerService;
    private final ObjectMapper objectMapper;

    public PaymentEventConsumer(
            PaymentRepository paymentRepository,
            AccountRepository accountRepository,
            TenantRepository tenantRepository,
            LedgerService ledgerService,
            ObjectMapper objectMapper
    ){
        this.paymentRepository = paymentRepository;
        this.accountRepository = accountRepository;
        this.tenantRepository = tenantRepository;
        this.ledgerService = ledgerService;
        this.objectMapper = objectMapper;
    }
    @KafkaListener(topics = KafkaTopics.PAYMENT_EVENTS, groupId = "paypus-payment-platform")
    @Transactional
    public void handlePaymentEvent(String message){
        PaymentCapturedPayload payload;
        try {
            payload = objectMapper.readValue(message, PaymentCapturedPayload.class);
        }catch (Exception e){
            throw new IllegalStateException("Failed to deserialize PaymentCapturedPayload: " + message, e);
        }

        Payment payment = paymentRepository.findById(payload.paymentId())
                .orElseThrow(() -> new IllegalStateException("Payment not found: " + payload.paymentId()));

        if (payment.getLedgerTransactionId() != null){
            return;
        }

        UUID tenantId  = payload.tenantId();

        UUID merchantBalanceAccountId = findOrCreateAccount(tenantId, AccountType.MERCHANT_BALANCE, payload.currency());
        UUID stripeClearingAccountId = findOrCreateAccount(tenantId, AccountType.STRIPE_CLEARING, payload.currency());

        List<LedgerEntryRequest> entries = List.of(
                new LedgerEntryRequest(merchantBalanceAccountId,payload.amount()),
                new LedgerEntryRequest(stripeClearingAccountId,payload.amount().negate())
        );

        LedgerTransaction transaction = ledgerService.post(
                tenantId,
                ReferenceType.PAYMENT,
                payment.getId(),
                "Payment captured via Stripe (async)",
                payload.currency(),
                entries
        );

        payment.setLedgerTransactionId(transaction.getId());
        paymentRepository.save(payment);
    }

    private UUID findOrCreateAccount(UUID tenantId, AccountType accountType, String currency){
        return accountRepository.findByTenantId(tenantId).stream()
                .filter(a -> a.getAccountType() == accountType && a.getCurrency().equals(currency))
                .findFirst()
                .map(Account::getId)
                .orElseGet(() -> createAccount(tenantId, accountType, currency));
    }

    private UUID createAccount(UUID tenantId, AccountType accountType, String currency){
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new IllegalStateException("Tenant not found: " + tenantId));

        Account account = new Account();
        account.setTenant(tenant);
        account.setAccountType(accountType);
        account.setCurrency(currency);
        account.setCreatedAt(OffsetDateTime.now());

        return accountRepository.save(account).getId();
    }
}
