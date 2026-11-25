-- A linked seller listing keeps its own product_id, stock, price and owner.
-- NULL means this row is an independent canonical catalog product.
-- Existing carts and orders continue referencing the exact purchased product_id.
alter table if exists products add column if not exists catalog_product_id bigint;
-- Index creation is deferred: Flyway also runs on fresh installs before Hibernate creates products.
