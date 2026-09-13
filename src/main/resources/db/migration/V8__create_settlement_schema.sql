-- V8__create_settlement_schema.sql
-- A settlement batch groups a tenant's unsettled captured payments into
-- one payout. Line items record exactly which payments were included,
-- for audit purposes. payment.settlement_batch_id marks a payment as
-- "claimed" by a batch, preventing it from being settled twice.

CREATE TABLE  settlement_batch (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    status VARCHAR(20) NOT NULL,
    total_amount NUMERIC(19,4) NOT NULL ,
    currency VARCHAR(3) NOT NULL,
    ledger_transaction_id UUID REFERENCES ledger_transaction(id),
    stripe_payout_id VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE settlement_line_item(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    settlement_batch_id UUID NOT NULL REFERENCES settlement_batch(id),
    payment_id UUID NOT NULL REFERENCES payment(id),
    amount NUMERIC(19,4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE payment
    ADD COLUMN settlement_batch_id UUID REFERENCES settlement_batch(id);

CREATE INDEX idx_settlement_batch_tenant_id ON settlement_batch(tenant_id);
CREATE INDEX idx_settlement_line_item_batch_id ON settlement_line_item(settlement_batch_id);
CREATE UNIQUE INDEX idx_settlement_line_item_payment_id ON settlement_line_item(payment_id);

ALTER TABLE settlement_batch
    ADD CONSTRAINT chk_settlement_batch_status
    CHECK ( status IN ('PENDING', 'PAID', 'FAILED'));