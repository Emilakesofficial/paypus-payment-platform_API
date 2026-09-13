-- V9__add_settlement_batch_failure_reason.sql
-- Captures the specific reason a Stripe payout failed, so failures are
-- diagnosable rather than just a generic FAILED status.

ALTER TABLE settlement_batch
    ADD COLUMN failure_reason VARCHAR(100);