-- Existing catalog remains live. New seller listings are reviewed before publication.
create table if not exists seller_profiles (
 id bigserial primary key,
 -- Flyway runs before Hibernate creates users on a fresh database.
 user_id bigint not null unique,
 business_name varchar(120) not null,
 contact_phone varchar(15) not null,
 gstin varchar(15),
 status varchar(16) not null default 'PENDING',
 submitted_at timestamptz not null default now(),
 reviewed_at timestamptz,
 reviewed_by varchar(100),
 review_note varchar(500)
);
alter table if exists products add column if not exists listing_status varchar(16) not null default 'APPROVED';
alter table if exists products add column if not exists listing_review_note varchar(500);
