package com.paypus.settlement;

public class NoUnsettledPaymentsException  extends RuntimeException {
    public NoUnsettledPaymentsException(String message) {
        super(message);
    }
}
