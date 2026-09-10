package com.paypus.payments;

public record RefundResult(
        Payment payment,
        String stripeRefundId
) {
}