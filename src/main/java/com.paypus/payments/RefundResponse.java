package com.paypus.payments;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RefundResponse(
        UUID paymentId,
        String stripeRefundId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        OffsetDateTime updatedAt

) {
}
