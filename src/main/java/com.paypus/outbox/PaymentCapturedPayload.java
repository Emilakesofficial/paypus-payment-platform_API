package com.paypus.outbox;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentCapturedPayload(
        UUID paymentId,
        UUID tenantId,
        BigDecimal amount,
        String currency
) {
}
