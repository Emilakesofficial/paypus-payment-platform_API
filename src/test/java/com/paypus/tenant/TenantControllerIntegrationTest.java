package com.paypus.tenant;

import com.paypus.AbstractIntegrationTest;
import com.paypus.common.ApiKeyHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TenantControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    private static final String RAW_KEY = "test-secret-key-123";

    @BeforeEach
    void setUp() {
        apiKeyRepository.deleteAll();
        tenantRepository.deleteAll();

        Tenant tenant = new Tenant();
        tenant.setName("Test Merchant");
        tenant.setCreatedAt(OffsetDateTime.now());
        tenant = tenantRepository.save(tenant);

        ApiKey apiKey = new ApiKey();
        apiKey.setTenant(tenant);
        apiKey.setKeyHash(ApiKeyHasher.hash(RAW_KEY));
        apiKey.setCreatedAt(OffsetDateTime.now());
        apiKeyRepository.save(apiKey);
    }

    @Test
    void validApiKey_returnsTenantInfo() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + RAW_KEY);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<TenantResponse> response = restTemplate.exchange(
                "/v1/me",
                org.springframework.http.HttpMethod.GET,
                request,
                TenantResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo("Test Merchant");
    }

    @Test
    void missingApiKey_returnsForbidden() {
        ResponseEntity<String> response = restTemplate.getForEntity("/v1/me", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void invalidApiKey_returnsForbidden() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer this-key-does-not-exist");
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/v1/me",
                org.springframework.http.HttpMethod.GET,
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}