-- Apply only to an approved database, separately from deployment.
BEGIN;
CREATE TABLE IF NOT EXISTS auth_rate_buckets (
    key VARCHAR(64) PRIMARY KEY,
    started_at BIGINT NOT NULL,
    attempts INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_auth_rate_buckets_started_at ON auth_rate_buckets(started_at);
ALTER TABLE legacy_claim_tickets ADD COLUMN IF NOT EXISTS approved_by VARCHAR(100);
ALTER TABLE legacy_claim_tickets ADD COLUMN IF NOT EXISTS approval_reference VARCHAR(100);
ALTER TABLE legacy_claim_tickets ADD COLUMN IF NOT EXISTS verification_method VARCHAR(50);
ALTER TABLE legacy_claim_tickets ADD COLUMN IF NOT EXISTS issued_at TIMESTAMPTZ NOT NULL DEFAULT now();
-- Old tickets lack independently recorded approval and cannot be redeemed.
COMMIT;
