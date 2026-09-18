package com.paypus.webhooks;

import com.paypus.tenant.Tenant;
import com.paypus.tenant.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class WebhookEndpointService {
    private final WebhookEndpointRepository webhookEndpointRepository;
    private final TenantRepository tenantRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public WebhookEndpointService(WebhookEndpointRepository webhookEndpointRepository, TenantRepository tenantRepository) {
        this.webhookEndpointRepository = webhookEndpointRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public WebhookEndpoint registerEndpoint(UUID tenantId, String url) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(()-> new IllegalArgumentException("Tenant not found: " +  tenantId));

        byte[] secretBytes = new byte[32];
        secureRandom.nextBytes(secretBytes);
        String secret = "whsec_" + HexFormat.of().formatHex(secretBytes);

        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setTenant(tenant);
        endpoint.setUrl(url);
        endpoint.setSecret(secret);
        endpoint.setActive(true);
        endpoint.setCreatedAt(OffsetDateTime.now());

        return webhookEndpointRepository.save(endpoint);
    }

}
