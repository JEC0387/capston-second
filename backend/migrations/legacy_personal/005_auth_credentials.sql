-- New credentials only. Existing users and their data are never claimed or deleted here.
BEGIN;

CREATE TABLE IF NOT EXISTS auth_credentials (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    recovery_hash VARCHAR(64) NOT NULL UNIQUE,
    data_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_auth_credentials_user_id ON auth_credentials (user_id);
CREATE UNIQUE INDEX IF NOT EXISTS ix_auth_credentials_token_hash ON auth_credentials (token_hash);
CREATE UNIQUE INDEX IF NOT EXISTS ix_auth_credentials_recovery_hash ON auth_credentials (recovery_hash);

CREATE TABLE IF NOT EXISTS legacy_claim_tickets (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    ticket_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS ix_legacy_claim_tickets_user_id ON legacy_claim_tickets (user_id);
CREATE UNIQUE INDEX IF NOT EXISTS ix_legacy_claim_tickets_ticket_hash ON legacy_claim_tickets (ticket_hash);

COMMIT;
