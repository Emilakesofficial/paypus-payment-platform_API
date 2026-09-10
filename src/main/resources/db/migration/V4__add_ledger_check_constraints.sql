
ALTER TABLE account
    ADD CONSTRAINT chk_account_type
        CHECK (account_type IN ('MERCHANT_BALANCE', 'PLATFORM_FEE', 'STRIPE_CLEARING', 'PAYOUT_CLEARING'));

ALTER TABLE ledger_transaction
    ADD CONSTRAINT chk_reference_type
        CHECK (reference_type IN ('PAYMENT', 'REFUND', 'SETTLEMENT', 'PAYOUT'));