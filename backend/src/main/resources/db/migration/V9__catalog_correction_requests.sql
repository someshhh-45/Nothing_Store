-- Admin-initiated listing corrections are independent workflow records.
-- Listing IDs, inventory counts, carts and historic order items are never migrated.
create table if not exists catalog_correction_requests (
 id bigserial primary key,
 product_id bigint not null,
 seller_id bigint not null,
 kind varchar(24) not null,
 status varchar(24) not null,
 message varchar(500) not null,
 seller_note varchar(500),
 review_note varchar(500),
 original_image varchar(2048),
 original_description varchar(255),
 original_name varchar(255),
 original_brand varchar(90),
 requested_by varchar(80) not null,
 requested_at timestamptz not null default now(),
 submitted_at timestamptz,
 reviewed_by varchar(80),
 reviewed_at timestamptz
);
create index if not exists idx_catalog_correction_seller on catalog_correction_requests(seller_id,status);
create index if not exists idx_catalog_correction_product on catalog_correction_requests(product_id,status);
