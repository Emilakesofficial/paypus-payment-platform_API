package com.paypus.webhooks;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetryBackoffTest {

    @Test
    void nextDelaySeconds_doublesEachAttempt() {
        assertThat(RetryBackoff.nextDelaySeconds(1)).isEqualTo(30);
        assertThat(RetryBackoff.nextDelaySeconds(2)).isEqualTo(60);
        assertThat(RetryBackoff.nextDelaySeconds(3)).isEqualTo(120);
        assertThat(RetryBackoff.nextDelaySeconds(4)).isEqualTo(240);
    }

    @Test
    void shouldDeadLetter_falseBeforeMaxAttempts() {
        assertThat(RetryBackoff.shouldDeadLetter(1)).isFalse();
        assertThat(RetryBackoff.shouldDeadLetter(4)).isFalse();
    }

    @Test
    void shouldDeadLetter_trueAtAndBeyondMaxAttempts() {
        assertThat(RetryBackoff.shouldDeadLetter(5)).isTrue();
        assertThat(RetryBackoff.shouldDeadLetter(6)).isTrue();
    }
}