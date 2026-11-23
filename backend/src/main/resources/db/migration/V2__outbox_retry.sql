-- Persist retry state and retain every unpublished event during broker outages.
ALTER TABLE nothing_outbox ADD COLUMN IF NOT EXISTS attempt_count integer NOT NULL DEFAULT 0;
ALTER TABLE nothing_outbox ADD COLUMN IF NOT EXISTS next_attempt_at timestamptz NOT NULL DEFAULT now();
CREATE INDEX IF NOT EXISTS nothing_outbox_retry_due ON nothing_outbox(next_attempt_at,created_at) WHERE published_at IS NULL;
