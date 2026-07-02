alter table app_users
    add column deleted_at timestamp with time zone;

create table account_deletion_requests (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references app_users (id) on delete cascade,
    token_hash varchar(64) not null,
    created_at timestamp with time zone not null default now(),
    expires_at timestamp with time zone not null,
    confirmed_at timestamp with time zone,
    cancelled_at timestamp with time zone,
    constraint uq_account_deletion_requests_token_hash unique (token_hash),
    constraint chk_account_deletion_requests_token_hash_not_blank check (btrim(token_hash) <> '')
);

create unique index uq_account_deletion_requests_active_user
    on account_deletion_requests (user_id)
    where confirmed_at is null and cancelled_at is null;

create index idx_account_deletion_requests_token_hash
    on account_deletion_requests (token_hash);
