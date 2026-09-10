package com.paypus.payments;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        String stripeClientSecret,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        OffsetDateTime createdAt
) {
}
