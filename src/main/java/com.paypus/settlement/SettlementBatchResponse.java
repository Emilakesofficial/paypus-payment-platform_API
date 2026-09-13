package com.paypus.settlement;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SettlementBatchResponse(
        UUID id,
        UUID tenantId,
        SettlementStatus status,
        BigDecimal totalAmount,
        String currency,
        List<UUID> paymentIds,
        OffsetDateTime createdAt
) {
}
