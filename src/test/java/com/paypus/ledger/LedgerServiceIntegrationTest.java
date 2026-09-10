package com.paypus.ledger;

import com.paypus.AbstractIntegrationTest;
import com.paypus.tenant.Tenant;
import com.paypus.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private AccountRepository accountRepository;

    private UUID tenantId;
    private UUID merchantBalanceAccountId;
    private UUID platformFeeAccountId;

    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant();
        tenant.setName("Ledger Test Merchant");
        tenant.setCreatedAt(OffsetDateTime.now());
        tenant = tenantRepository.save(tenant);
        tenantId = tenant.getId();

        Account merchantBalance = new Account();
        merchantBalance.setTenant(tenant);
        merchantBalance.setAccountType(AccountType.MERCHANT_BALANCE);
        merchantBalance.setCurrency("USD");
        merchantBalance.setCreatedAt(OffsetDateTime.now());
        merchantBalance = accountRepository.save(merchantBalance);
        merchantBalanceAccountId = merchantBalance.getId();

        Account platformFee = new Account();
        platformFee.setTenant(tenant);
        platformFee.setAccountType(AccountType.PLATFORM_FEE);
        platformFee.setCurrency("USD");
        platformFee.setCreatedAt(OffsetDateTime.now());
        platformFee = accountRepository.save(platformFee);
        platformFeeAccountId = platformFee.getId();
    }

    @Test
    void balancedEntries_postSuccessfully() {
        List<LedgerEntryRequest> entries = List.of(
                new LedgerEntryRequest(merchantBalanceAccountId, new BigDecimal("95.00")),
                new LedgerEntryRequest(platformFeeAccountId, new BigDecimal("-95.00"))
        );

        LedgerTransaction transaction = ledgerService.post(
                tenantId, ReferenceType.PAYMENT, UUID.randomUUID(), "test payment", "USD", entries
        );

        assertThat(transaction.getId()).isNotNull();
        assertThat(accountRepository.computeBalance(merchantBalanceAccountId))
                .isEqualByComparingTo("95.00");
        assertThat(accountRepository.computeBalance(platformFeeAccountId))
                .isEqualByComparingTo("-95.00");
    }

    @Test
    void unbalancedEntries_areRejected() {
        List<LedgerEntryRequest> entries = List.of(
                new LedgerEntryRequest(merchantBalanceAccountId, new BigDecimal("100.00")),
                new LedgerEntryRequest(platformFeeAccountId, new BigDecimal("-95.00"))
        );

        assertThatThrownBy(() ->
                ledgerService.post(tenantId, ReferenceType.PAYMENT, UUID.randomUUID(), "bad payment", "USD", entries)
        ).isInstanceOf(UnbalancedTransactionException.class);

        assertThat(accountRepository.computeBalance(merchantBalanceAccountId))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void balanceReflectsMultiplePostedTransactions() {
        postSimplePayment(new BigDecimal("50.00"));
        postSimplePayment(new BigDecimal("30.00"));
        postSimplePayment(new BigDecimal("20.00"));

        assertThat(accountRepository.computeBalance(merchantBalanceAccountId))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void concurrentPostings_noLostUpdates() throws InterruptedException {
        int threadCount = 20;
        BigDecimal amountPerPosting = new BigDecimal("10.00");

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    postSimplePayment(amountPerPosting);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        BigDecimal expectedTotal = amountPerPosting.multiply(new BigDecimal(threadCount));
        assertThat(accountRepository.computeBalance(merchantBalanceAccountId))
                .isEqualByComparingTo(expectedTotal);
    }

    private void postSimplePayment(BigDecimal amount) {
        List<LedgerEntryRequest> entries = List.of(
                new LedgerEntryRequest(merchantBalanceAccountId, amount),
                new LedgerEntryRequest(platformFeeAccountId, amount.negate())
        );
        ledgerService.post(tenantId, ReferenceType.PAYMENT, UUID.randomUUID(), "payment", "USD", entries);
    }
}