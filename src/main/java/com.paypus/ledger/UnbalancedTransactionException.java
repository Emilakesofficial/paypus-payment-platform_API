package com.paypus.ledger;

import java.math.BigDecimal;

public class UnbalancedTransactionException extends RuntimeException {
    public UnbalancedTransactionException(BigDecimal actualSum) {
        super("Ledger transaction entries must sum to zero, but summed to: " +  actualSum);
    }
}
