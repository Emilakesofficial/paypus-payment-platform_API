package com.paypus.ledger;

import com.paypus.tenant.Tenant;
import com.paypus.tenant.TenantRepository;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class LedgerService {
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;
    private final TenantRepository tenantRepository;

    public LedgerService(
            LedgerTransactionRepository ledgerTransactionRepository,
            LedgerEntryRepository ledgerEntryRepository,
            AccountRepository accountRepository,
            TenantRepository tenantRepository
    ){
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountRepository = accountRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public LedgerTransaction post(
            UUID tenantId,
            ReferenceType referenceType,
            UUID referenceId,
            String description,
            String currency,
            List<LedgerEntryRequest> entries
    ) {
        BigDecimal sum = entries.stream()
                .map(LedgerEntryRequest::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (sum.compareTo(BigDecimal.ZERO) != 0){
            throw new UnbalancedTransactionException(sum);
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

                LedgerTransaction transaction = new LedgerTransaction();
                transaction.setTenant(tenant);
                transaction.setReferenceType(referenceType);
                transaction.setReferenceId(referenceId);
                transaction.setDescription(description);
                transaction.setCreatedAt(OffsetDateTime.now());
                transaction = ledgerTransactionRepository.save(transaction);

                for (LedgerEntryRequest entryRequest:  entries) {
                    Account account = accountRepository.findById(entryRequest.accountId())
                            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + entryRequest.accountId()));

                    LedgerEntry entry = new LedgerEntry();
                    entry.setLedgerTransaction(transaction);
                    entry.setAccount(account);
                    entry.setAmount(entryRequest.amount());
                    entry.setCurrency(currency);
                    entry.setCreatedAt(OffsetDateTime.now());
                    ledgerEntryRepository.save(entry);
                }
                return transaction;
    }
}
