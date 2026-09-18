-- V12__create_webhook_delivery.sql
-- Tracks each merchant-facing webhook delivery: what event, to which
-- endpoint, current status, attempt count, and when to retry next.

CREATE TABLE webhook_delivery (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    webhook_endpoint_id UUID NOT NULL REFERENCES webhook_endpoint(id),
    outbox_event_id UUID NOT NULL REFERENCES outbox_event(id),
    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ,
    last_response_status INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_webhook_delivery_next_attempts ON webhook_delivery(next_attempt_at) WHERE status = 'PENDING';
CREATE UNIQUE INDEX idx_webhook_delivery_endpoint_event ON webhook_delivery(webhook_endpoint_id, outbox_event_id);

ALTER TABLE webhook_delivery
    ADD CONSTRAINT chk_webhook_delivery_status
    CHECK (status IN ('PENDING', 'DELIVERED', 'DEAD_LETTERED'));