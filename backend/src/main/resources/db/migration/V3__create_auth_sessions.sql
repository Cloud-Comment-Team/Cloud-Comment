create table auth_sessions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references app_users (id) on delete cascade,
    token_hash varchar(64) not null,
    created_at timestamp with time zone not null default now(),
    expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    constraint uq_auth_sessions_token_hash unique (token_hash),
    constraint chk_auth_sessions_token_hash_not_blank check (btrim(token_hash) <> ''),
    constraint chk_auth_sessions_lifetime check (expires_at > created_at),
    constraint chk_auth_sessions_revoked_after_created check (revoked_at is null or revoked_at >= created_at)
);

create index idx_auth_sessions_user_id on auth_sessions (user_id);
create index idx_auth_sessions_user_active on auth_sessions (user_id, expires_at)
    where revoked_at is null;
