-- Admin review decisions are audit metadata, not product mutations.
-- Existing product, seller, inventory, cart and order tables stay unchanged.
create table if not exists catalog_quality_reviews (
 issue_key varchar(72) primary key,
 status varchar(24) not null,
 note varchar(500),
 reviewed_by varchar(80) not null,
 updated_at timestamptz not null default now()
);
create index if not exists idx_catalog_quality_reviews_status on catalog_quality_reviews(status);
