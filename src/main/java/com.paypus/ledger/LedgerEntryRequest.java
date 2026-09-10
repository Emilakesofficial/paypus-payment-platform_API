package com.paypus.ledger;

import java.math.BigDecimal;
import java.util.UUID;

public record LedgerEntryRequest(
        UUID accountId,
        BigDecimal amount
) {
}
