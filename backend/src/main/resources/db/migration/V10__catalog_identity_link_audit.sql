-- Immutable admin audit trail for linking confirmed duplicate catalog identities.
create table if not exists catalog_identity_link_audit (
 id bigserial primary key,
 product_id bigint not null,
 canonical_product_id bigint not null,
 linked_by varchar(80) not null,
 reason varchar(500) not null,
 linked_at timestamptz not null default now()
);
create index if not exists idx_catalog_link_audit_product on catalog_identity_link_audit(product_id);
