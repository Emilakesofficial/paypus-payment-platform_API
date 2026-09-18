package com.paypus.webhooks;

import com.paypus.AbstractIntegrationTest;
import com.paypus.tenant.Tenant;
import com.paypus.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookEndpointServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebhookEndpointService webhookEndpointService;

    @Autowired
    private TenantRepository tenantRepository;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant();
        tenant.setName("Webhook Endpoint Test Merchant");
        tenant.setCreatedAt(OffsetDateTime.now());
        tenant = tenantRepository.save(tenant);
        tenantId = tenant.getId();
    }

    @Test
    void registerEndpoint_createsActiveEndpointWithSecret() {
        WebhookEndpoint endpoint = webhookEndpointService.registerEndpoint(
                tenantId, "https://example.com/webhooks"
        );

        assertThat(endpoint.getId()).isNotNull();
        assertThat(endpoint.getUrl()).isEqualTo("https://example.com/webhooks");
        assertThat(endpoint.isActive()).isTrue();
        assertThat(endpoint.getSecret()).startsWith("whsec_");
    }

    @Test
    void registerEndpoint_generatesUniqueSecretsForEachCall() {
        WebhookEndpoint first = webhookEndpointService.registerEndpoint(
                tenantId, "https://example.com/webhooks-a"
        );
        WebhookEndpoint second = webhookEndpointService.registerEndpoint(
                tenantId, "https://example.com/webhooks-b"
        );

        assertThat(first.getSecret()).isNotEqualTo(second.getSecret());
    }

    @Test
    void registerEndpoint_unknownTenant_throwsIllegalArgumentException() {
        UUID unknownTenantId = UUID.randomUUID();

        assertThatThrownBy(() ->
                webhookEndpointService.registerEndpoint(unknownTenantId, "https://example.com/webhooks")
        ).isInstanceOf(IllegalArgumentException.class);
    }
}