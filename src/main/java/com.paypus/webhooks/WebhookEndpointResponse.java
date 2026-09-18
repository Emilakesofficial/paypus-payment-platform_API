package com.paypus.webhooks;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WebhookEndpointResponse(
        UUID id,
        String url,
        String secret,
        boolean isActive,
        OffsetDateTime createdAt
) {
}
