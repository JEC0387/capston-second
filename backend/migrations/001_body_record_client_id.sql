-- Apply once to a DB already initialized by the teammate's project.
-- Existing measurements are preserved. New installations use Base.metadata.create_all.
BEGIN;
ALTER TABLE body_measurements ADD COLUMN IF NOT EXISTS client_id VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS uq_body_user_client
    ON body_measurements (user_id, client_id);
COMMIT;
