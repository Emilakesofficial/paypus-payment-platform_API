-- V11__create_webhook_endpoint.sql
-- A tenant registers one URL where we'll deliver their webhooks, plus a
-- secret they use to verify our HMAC signature on incoming deliveries.

CREATE TABLE webhook_endpoint (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    url VARCHAR(255) NOT NULL,
    secret VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_webhook_endpoint_tenant_id ON webhook_endpoint(tenant_id);