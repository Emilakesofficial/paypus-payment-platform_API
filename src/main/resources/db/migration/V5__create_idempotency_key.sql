CREATE TABLE  idempotency_key (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response_status INT,
    response_body TEXT,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_idempotency_key_tenant_key ON idempotency_key(tenant_id, idempotency_key);

ALTER TABLE idempotency_key
    ADD CONSTRAINT chk_idempotency_status
    CHECK ( status IN ('IN_PROGRESS', 'COMPLETED'));