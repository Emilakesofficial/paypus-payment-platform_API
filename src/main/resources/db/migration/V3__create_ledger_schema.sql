CREATE TABLE account (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    account_type VARCHAR(50) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ledger_transaction (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    reference_type VARCHAR(50) NOT NULL,
    reference_id UUID,
    description VARCHAR(500),
    created_At TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ledger_entry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ledger_transaction_id UUID NOT NULL REFERENCES ledger_transaction(id),
    account_id UUID NOT NULL REFERENCES account(id),
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_account_tenant_id ON account(tenant_id);
CREATE INDEX idx_ledger_transaction_tenant_id ON ledger_transaction(tenant_id);
CREATE INDEX idx_ledger_entry_transaction_id ON ledger_entry(ledger_transaction_id);
CREATE INDEX idx_ledger_entry_account_id ON ledger_entry(account_id);