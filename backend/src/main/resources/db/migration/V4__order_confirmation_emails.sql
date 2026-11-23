-- Durable per-order confirmation state. Created in the same transaction as the order.
-- Fresh bootstrap runs Flyway before Hibernate creates orders, so do not declare
-- a foreign key against a JPA-managed table in this migration.
-- Nothing is backfilled: old historical orders must not be emailed unexpectedly.
CREATE TABLE IF NOT EXISTS order_confirmation_emails (
  order_id bigint PRIMARY KEY,
  status varchar(12) NOT NULL DEFAULT 'PENDING',
  attempts integer NOT NULL DEFAULT 0,
  next_attempt_at timestamptz NOT NULL DEFAULT now(),
  claimed_at timestamptz,
  sent_at timestamptz,
  last_error_code varchar(80),
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT email_delivery_valid_status CHECK (status IN ('PENDING','SENDING','SENT')),
  CONSTRAINT email_delivery_valid_attempts CHECK (attempts>=0)
);
CREATE INDEX IF NOT EXISTS order_confirmation_email_due_idx
 ON order_confirmation_emails(status,next_attempt_at,claimed_at);
-- Ordered name is captured at checkout, not based on a mutable product title.
ALTER TABLE IF EXISTS order_items ADD COLUMN IF NOT EXISTS ordered_product_name varchar(255);
-- Delivery address is captured when checkout completes, so edits don't rewrite receipts.
ALTER TABLE IF EXISTS orders ADD COLUMN IF NOT EXISTS shipping_address_snapshot varchar(1000);
