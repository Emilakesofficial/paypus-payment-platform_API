CREATE TABLE payment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    stripe_payment_intent_id VARCHAR(255) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    ledger_transaction_id UUID REFERENCES ledger_transaction(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPtz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_payment_stripe_payment_intent_id ON payment(stripe_payment_intent_id);
CREATE INDEX idx_payment_tenant_id ON payment(tenant_id);

ALTER TABLE payment
    ADD CONSTRAINT chk_payment_status
    CHECK ( status IN ('PENDING', 'AUTHORIZED', 'CAPTURED', 'FAILED', 'REFUNDED'));