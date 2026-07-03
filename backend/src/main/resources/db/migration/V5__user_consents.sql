create table user_consents (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references app_users (id) on delete cascade,
    privacy_policy_version varchar(64) not null,
    terms_version varchar(64) not null,
    source varchar(16) not null,
    accepted_at timestamp with time zone not null default now(),
    constraint chk_user_consents_source check (source in ('ADMIN', 'WIDGET')),
    constraint chk_user_consents_privacy_policy_version_not_blank check (btrim(privacy_policy_version) <> ''),
    constraint chk_user_consents_terms_version_not_blank check (btrim(terms_version) <> '')
);

create index idx_user_consents_user_id on user_consents (user_id);
