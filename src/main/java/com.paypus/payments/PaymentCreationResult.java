package com.paypus.payments;

public record PaymentCreationResult(
        Payment payment,
        String stripeClientSecret
) {
}
