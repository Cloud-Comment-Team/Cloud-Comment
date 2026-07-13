alter table auth_sessions
    add column site_id uuid references sites (id) on delete cascade,
    add column origin varchar(255);

alter table auth_sessions
    drop constraint chk_auth_sessions_audience;

alter table auth_sessions
    add constraint chk_auth_sessions_audience
        check (audience in ('ADMIN', 'WIDGET', 'WIDGET_FRAME', 'LEGACY'));

update auth_sessions
set revoked_at = greatest(now(), created_at)
where audience = 'WIDGET'
  and revoked_at is null;

update auth_sessions
set audience = 'LEGACY'
where audience = 'WIDGET';

create table widget_bootstrap_tickets (
    id uuid primary key default gen_random_uuid(),
    ticket_hash varchar(64) not null,
    site_id uuid not null references sites (id) on delete cascade,
    origin varchar(255) not null,
    canonical_page_url text not null,
    page_url_hash varchar(64) not null,
    public_key_fingerprint varchar(43) not null,
    public_key_spki bytea not null,
    created_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    consumed_at timestamp with time zone,
    constraint uq_widget_bootstrap_tickets_hash unique (ticket_hash),
    constraint chk_widget_bootstrap_ticket_hash check (ticket_hash ~ '^[0-9a-f]{64}$'),
    constraint chk_widget_bootstrap_origin check (btrim(origin) <> ''),
    constraint chk_widget_bootstrap_page_url check (btrim(canonical_page_url) <> ''),
    constraint chk_widget_bootstrap_page_hash check (page_url_hash ~ '^[0-9a-f]{64}$'),
    constraint chk_widget_bootstrap_key_fingerprint check (public_key_fingerprint ~ '^[A-Za-z0-9_-]{43}$'),
    constraint chk_widget_bootstrap_public_key check (octet_length(public_key_spki) > 0),
    constraint chk_widget_bootstrap_lifetime check (expires_at > created_at),
    constraint chk_widget_bootstrap_consumed_at check (consumed_at is null or consumed_at >= created_at)
);

create index idx_widget_bootstrap_tickets_active
    on widget_bootstrap_tickets (ticket_hash, site_id, expires_at)
    where consumed_at is null;

create index idx_widget_bootstrap_tickets_expires_at
    on widget_bootstrap_tickets (expires_at);

create unique index uq_widget_bootstrap_outstanding_key
    on widget_bootstrap_tickets (site_id, origin, public_key_fingerprint)
    where consumed_at is null;

create table widget_frame_contexts (
    id uuid primary key default gen_random_uuid(),
    token_hash varchar(64) not null,
    site_id uuid not null references sites (id) on delete cascade,
    origin varchar(255) not null,
    page_url_hash varchar(64) not null,
    created_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    constraint uq_widget_frame_contexts_hash unique (token_hash),
    constraint chk_widget_frame_context_hash check (token_hash ~ '^[0-9a-f]{64}$'),
    constraint chk_widget_frame_context_origin check (btrim(origin) <> ''),
    constraint chk_widget_frame_context_page_hash check (page_url_hash ~ '^[0-9a-f]{64}$'),
    constraint chk_widget_frame_context_lifetime check (expires_at > created_at)
);

create index idx_widget_frame_contexts_active
    on widget_frame_contexts (token_hash, site_id, expires_at);

create index idx_widget_frame_contexts_expires_at
    on widget_frame_contexts (expires_at);

alter table auth_sessions
    add constraint chk_auth_sessions_scope
        check (
            (
                audience = 'WIDGET_FRAME'
                and site_id is not null
                and origin is not null
                and btrim(origin) <> ''
            )
            or
            (
                audience in ('ADMIN', 'LEGACY', 'WIDGET')
                and site_id is null
                and origin is null
            )
        );

create index idx_auth_sessions_active_widget_scope
    on auth_sessions (site_id, origin, expires_at)
    where audience = 'WIDGET_FRAME' and revoked_at is null;
