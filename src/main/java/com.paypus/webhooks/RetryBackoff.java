package com.paypus.webhooks;

public final class RetryBackoff {
    private static final int MAX_ATTEMPTS = 5;
    private static final long BASE_DELAY_SECONDS = 30;

    private RetryBackoff() {

    }

    public static boolean shouldDeadLetter(int attemptCount){
        return attemptCount >= MAX_ATTEMPTS;
    }

    public static long nextDelaySeconds(int attemptCount){
        return BASE_DELAY_SECONDS * (long) Math.pow(2, attemptCount - 1);
    }
}
