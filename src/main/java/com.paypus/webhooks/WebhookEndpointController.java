package com.paypus.webhooks;

import com.paypus.common.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class WebhookEndpointController {
    public final WebhookEndpointService webhookEndpointService;

    public WebhookEndpointController(WebhookEndpointService webhookEndpointService) {
        this.webhookEndpointService = webhookEndpointService;
    }

    @PostMapping("v1/webhook-endpoints")
    public ResponseEntity<WebhookEndpointResponse> registerWebhook(
            @RequestBody RegisterWebhookEndpointRequest request
    ){
        UUID tenantId = TenantContext.getTenantId();

        WebhookEndpoint endpoint = webhookEndpointService.registerEndpoint(tenantId, request.url());

        WebhookEndpointResponse response = new WebhookEndpointResponse(
            endpoint.getId(),
            endpoint.getUrl(),
            endpoint.getSecret(),
            endpoint.isActive(),
            endpoint.getCreatedAt()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
