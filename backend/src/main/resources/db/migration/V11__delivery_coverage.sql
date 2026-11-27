-- Seller-specific delivery rules. seller_key=0 denotes platform-owned catalog inventory.
-- No assumptions about dispatch city imply serviceability.
create table if not exists delivery_coverage_zones (
 id bigserial primary key,
 seller_key bigint not null,
 pin_from varchar(6) not null,
 pin_to varchar(6) not null,
 eta_min_days integer not null,
 eta_max_days integer not null,
 enabled boolean not null default true,
 updated_at timestamptz not null default now(),
 constraint ck_delivery_pins check (pin_from ~ '^[1-9][0-9]{5}$' and pin_to ~ '^[1-9][0-9]{5}$' and pin_from <= pin_to),
 constraint ck_delivery_eta check (eta_min_days between 1 and 30 and eta_max_days between eta_min_days and 45)
);
create index if not exists idx_delivery_zone_seller on delivery_coverage_zones(seller_key,enabled,pin_from,pin_to);
