package com.paypus.idempotency;

public record IdempotencyResult(
        boolean isReplay,
        Integer responseStatus,
        String responseBody
) {
}
