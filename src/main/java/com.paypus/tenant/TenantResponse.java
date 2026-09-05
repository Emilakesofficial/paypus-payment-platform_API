package com.paypus.tenant;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TenantResponse(
        UUID id,
        String name,
        OffsetDateTime createdAt
) {
}
