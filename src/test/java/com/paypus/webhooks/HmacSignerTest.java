package com.paypus.webhooks;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSignerTest {

    @Test
    void sign_producesConsistentSignatureForSameInputs() {
        String signature1 = HmacSigner.sign("{\"amount\":100}", "whsec_test123", 1700000000L);
        String signature2 = HmacSigner.sign("{\"amount\":100}", "whsec_test123", 1700000000L);

        assertThat(signature1).isEqualTo(signature2);
    }

    @Test
    void sign_producesDifferentSignatureForDifferentPayload() {
        String signature1 = HmacSigner.sign("{\"amount\":100}", "whsec_test123", 1700000000L);
        String signature2 = HmacSigner.sign("{\"amount\":200}", "whsec_test123", 1700000000L);

        assertThat(signature1).isNotEqualTo(signature2);
    }

    @Test
    void sign_producesDifferentSignatureForDifferentSecret() {
        String signature1 = HmacSigner.sign("{\"amount\":100}", "whsec_secretA", 1700000000L);
        String signature2 = HmacSigner.sign("{\"amount\":100}", "whsec_secretB", 1700000000L);

        assertThat(signature1).isNotEqualTo(signature2);
    }

    @Test
    void sign_includesTimestampAndVersionInExpectedFormat() {
        String signature = HmacSigner.sign("{\"amount\":100}", "whsec_test123", 1700000000L);

        assertThat(signature).startsWith("t=1700000000,v1=");
    }
}