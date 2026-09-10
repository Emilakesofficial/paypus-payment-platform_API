package com.paypus.payments;

import com.paypus.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class StripeWebhookControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void invalidSignature_isRejectedWithBadRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Stripe-Signature", "t=1234,v1=not_a_real_signature");
        HttpEntity<String> request = new HttpEntity<>("{\"type\":\"payment_intent.succeeded\"}", headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/v1/webhooks/stripe",
                HttpMethod.POST,
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void noApiKeyRequired_endpointIsPubliclyReachable() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Stripe-Signature", "t=1234,v1=not_a_real_signature");
        HttpEntity<String> request = new HttpEntity<>("{\"type\":\"payment_intent.succeeded\"}", headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/v1/webhooks/stripe",
                HttpMethod.POST,
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }
}