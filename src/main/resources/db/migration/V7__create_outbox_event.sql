-- V7__create_outbox_event.sql
-- Transactional outbox: events are written here in the SAME transaction
-- as the business data change they describe, guaranteeing we never lose
-- an event even if the downstream Kafka publish fails or is delayed.

CREATE TABLE outbox_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_outbox_event_unpublished ON outbox_event(created_at) WHERE published_at is NULL;