-- V10__fix_settlement_batch_status_constraint.sql
-- V8's CHECK constraint was written with 'PAID' instead of 'PAID_OUT',
-- not matching the Java SettlementStatus enum. Fixing it here.

ALTER TABLE settlement_batch
DROP CONSTRAINT chk_settlement_batch_status;

ALTER TABLE settlement_batch
    ADD CONSTRAINT chk_settlement_batch_status
        CHECK (status IN ('PENDING', 'PAID_OUT', 'FAILED'));