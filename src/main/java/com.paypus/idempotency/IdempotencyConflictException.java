package com.paypus.idempotency;

public class IdempotencyConflictException  extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
