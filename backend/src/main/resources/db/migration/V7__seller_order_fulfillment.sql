-- Seller-scoped fulfillment; existing global tracking is retained for compatibility.
-- Flyway precedes Hibernate table creation on new installations, so no FK is declared here.
create table if not exists seller_order_fulfillments (
 id bigserial primary key,
 order_id bigint not null,
 seller_id bigint not null,
 stage varchar(32) not null default 'PLACED',
 carrier varchar(80),
 tracking_number varchar(60),
 note varchar(180),
 updated_at timestamptz not null default now(),
 constraint uk_seller_order_fulfillment unique(order_id,seller_id)
);
create index if not exists idx_seller_order_fulfillments_seller on seller_order_fulfillments(seller_id,order_id);
