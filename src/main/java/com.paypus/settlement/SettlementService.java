package com.paypus.settlement;

import com.paypus.ledger.Account;
import com.paypus.ledger.AccountRepository;
import com.paypus.ledger.AccountType;
import com.paypus.ledger.LedgerEntryRequest;
import com.paypus.ledger.LedgerService;
import com.paypus.ledger.LedgerTransaction;
import com.paypus.ledger.ReferenceType;
import com.paypus.payments.Payment;
import com.paypus.payments.PaymentRepository;
import com.paypus.payments.PaymentStatus;
import com.paypus.tenant.Tenant;
import com.paypus.tenant.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stripe.exception.StripeException;
import com.stripe.model.Payout;
import com.stripe.param.PayoutCreateParams;
import com.paypus.payments.PaymentProcessingException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SettlementService {

    private final PaymentRepository paymentRepository;
    private final AccountRepository accountRepository;
    private final TenantRepository tenantRepository;
    private final SettlementBatchRepository settlementBatchRepository;
    private final SettlementLineItemRepository settlementLineItemRepository;
    private final LedgerService ledgerService;

    public SettlementService(
            PaymentRepository paymentRepository,
            AccountRepository accountRepository,
            TenantRepository tenantRepository,
            SettlementBatchRepository settlementBatchRepository,
            SettlementLineItemRepository settlementLineItemRepository,
            LedgerService ledgerService
    ) {
        this.paymentRepository = paymentRepository;
        this.accountRepository = accountRepository;
        this.tenantRepository = tenantRepository;
        this.settlementBatchRepository = settlementBatchRepository;
        this.settlementLineItemRepository = settlementLineItemRepository;
        this.ledgerService = ledgerService;
    }

    @Transactional
    public SettlementBatch createSettlementBatch(UUID tenantId, String currency) {
        List<Payment> unsettledPayments = paymentRepository
                .findByTenantIdAndStatusAndSettlementBatchIdIsNull(tenantId, PaymentStatus.CAPTURED)
                .stream()
                .filter(p -> p.getCurrency().equals(currency))
                .collect(Collectors.toList());

        if (unsettledPayments.isEmpty()) {
            throw new NoUnsettledPaymentsException(
                    "No unsettled CAPTURED payments found for tenant " + tenantId + " in currency " + currency
            );
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        BigDecimal totalAmount = unsettledPayments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        SettlementBatch batch = new SettlementBatch();
        batch.setTenant(tenant);
        batch.setStatus(SettlementStatus.PENDING);
        batch.setTotalAmount(totalAmount);
        batch.setCurrency(currency);
        batch.setCreatedAt(OffsetDateTime.now());
        batch.setUpdatedAt(OffsetDateTime.now());
        batch = settlementBatchRepository.save(batch);

        for (Payment payment : unsettledPayments) {
            SettlementLineItem lineItem = new SettlementLineItem();
            lineItem.setSettlementBatch(batch);
            lineItem.setPayment(payment);
            lineItem.setAmount(payment.getAmount());
            lineItem.setCreatedAt(OffsetDateTime.now());
            settlementLineItemRepository.save(lineItem);

            payment.setSettlementBatchId(batch.getId());
            paymentRepository.save(payment);
        }

        UUID merchantBalanceAccountId = findAccount(tenantId, AccountType.MERCHANT_BALANCE, currency);
        UUID payoutClearingAccountId = findOrCreateAccount(tenantId, AccountType.PAYOUT_CLEARING, currency);

        List<LedgerEntryRequest> entries = List.of(
                new LedgerEntryRequest(merchantBalanceAccountId, totalAmount.negate()),
                new LedgerEntryRequest(payoutClearingAccountId, totalAmount)
        );

        LedgerTransaction transaction = ledgerService.post(
                tenantId,
                ReferenceType.SETTLEMENT,
                batch.getId(),
                "Settlement batch for " + unsettledPayments.size() + " payment(s)",
                currency,
                entries
        );

        batch.setLedgerTransactionId(transaction.getId());
        batch.setUpdatedAt(OffsetDateTime.now());
        batch = settlementBatchRepository.save(batch);

        long amountInCents = totalAmount
                .setScale(2, RoundingMode.UNNECESSARY)
                .multiply(new BigDecimal(100))
                .longValueExact();

        Payout stripePayout;
        try {
            PayoutCreateParams payoutParams = PayoutCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency(currency)
                    .build();

            stripePayout = Payout.create(payoutParams);
        }catch (StripeException e) {
            throw new PaymentProcessingException(
                    "Failed to create Stripe payout", e
            );
        }
        batch.setStripePayoutId(stripePayout.getId());
        batch.setUpdatedAt(OffsetDateTime.now());
        batch = settlementBatchRepository.save(batch);

        return batch;
    }

    private UUID findAccount(UUID tenantId, AccountType accountType, String currency) {
        return accountRepository.findByTenantId(tenantId).stream()
                .filter(a -> a.getAccountType() == accountType && a.getCurrency().equals(currency))
                .findFirst()
                .map(Account::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "No " + accountType + " account found for tenant " + tenantId
                ));
    }

    private UUID findOrCreateAccount(UUID tenantId, AccountType accountType, String currency) {
        return accountRepository.findByTenantId(tenantId).stream()
                .filter(a -> a.getAccountType() == accountType && a.getCurrency().equals(currency))
                .findFirst()
                .map(Account::getId)
                .orElseGet(() -> createAccount(tenantId, accountType, currency));
    }

    private UUID createAccount(UUID tenantId, AccountType accountType, String currency) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        Account account = new Account();
        account.setTenant(tenant);
        account.setAccountType(accountType);
        account.setCurrency(currency);
        account.setCreatedAt(OffsetDateTime.now());

        return accountRepository.save(account).getId();
    }
}