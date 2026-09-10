package com.paypus.payments;

import java.math.BigDecimal;

public record CreatePaymentRequest(
        BigDecimal amount,
        String currency
) {
}
