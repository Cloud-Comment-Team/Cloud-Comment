create extension if not exists pgcrypto;

create table app_users (
    id uuid primary key default gen_random_uuid(),
    email varchar(320) not null,
    password_hash varchar(255) not null,
    display_name varchar(120),
    is_enabled boolean not null default true,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    constraint uq_app_users_email unique (email),
    constraint chk_app_users_email_not_blank check (btrim(email) <> ''),
    constraint chk_app_users_password_hash_not_blank check (btrim(password_hash) <> '')
);

create table user_roles (
    user_id uuid not null references app_users (id) on delete cascade,
    role varchar(32) not null,
    created_at timestamp with time zone not null default now(),
    primary key (user_id, role),
    constraint chk_user_roles_role check (role in ('OWNER', 'COMMENTER', 'MODERATOR'))
);

create table sites (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references app_users (id) on delete cascade,
    name varchar(160) not null,
    domain varchar(255) not null,
    public_key varchar(64) not null,
    moderation_mode varchar(32) not null default 'PRE_MODERATION',
    is_active boolean not null default true,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    constraint uq_sites_owner_domain unique (owner_id, domain),
    constraint uq_sites_public_key unique (public_key),
    constraint chk_sites_name_not_blank check (btrim(name) <> ''),
    constraint chk_sites_domain_not_blank check (btrim(domain) <> ''),
    constraint chk_sites_public_key_not_blank check (btrim(public_key) <> ''),
    constraint chk_sites_moderation_mode check (moderation_mode in ('PRE_MODERATION', 'POST_MODERATION', 'DISABLED'))
);

create table site_allowed_origins (
    id uuid primary key default gen_random_uuid(),
    site_id uuid not null references sites (id) on delete cascade,
    origin varchar(255) not null,
    created_at timestamp with time zone not null default now(),
    constraint uq_site_allowed_origins_site_origin unique (site_id, origin),
    constraint chk_site_allowed_origins_origin_not_blank check (btrim(origin) <> '')
);

create table pages (
    id uuid primary key default gen_random_uuid(),
    site_id uuid not null references sites (id) on delete cascade,
    url text not null,
    title varchar(255),
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    constraint uq_pages_site_url unique (site_id, url),
    constraint chk_pages_url_not_blank check (btrim(url) <> '')
);

create table comments (
    id uuid primary key default gen_random_uuid(),
    page_id uuid not null references pages (id) on delete cascade,
    parent_id uuid references comments (id) on delete cascade,
    author_user_id uuid references app_users (id) on delete set null,
    author_name varchar(120),
    author_email varchar(320),
    body text not null,
    status varchar(32) not null default 'PENDING',
    moderation_reason text,
    ip_hash varchar(128),
    user_agent varchar(512),
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    moderated_at timestamp with time zone,
    constraint chk_comments_body_not_blank check (btrim(body) <> ''),
    constraint chk_comments_status check (status in ('PENDING', 'APPROVED', 'REJECTED', 'HIDDEN', 'SPAM')),
    constraint chk_comments_author_name_not_blank check (author_name is null or btrim(author_name) <> ''),
    constraint chk_comments_author_email_not_blank check (author_email is null or btrim(author_email) <> '')
);

create table moderation_actions (
    id uuid primary key default gen_random_uuid(),
    comment_id uuid not null references comments (id) on delete cascade,
    moderator_id uuid references app_users (id) on delete set null,
    action varchar(32) not null,
    from_status varchar(32) not null,
    to_status varchar(32) not null,
    reason text,
    created_at timestamp with time zone not null default now(),
    constraint chk_moderation_actions_action check (action in ('APPROVE', 'REJECT', 'HIDE', 'MARK_SPAM', 'RESTORE')),
    constraint chk_moderation_actions_from_status check (from_status in ('PENDING', 'APPROVED', 'REJECTED', 'HIDDEN', 'SPAM')),
    constraint chk_moderation_actions_to_status check (to_status in ('PENDING', 'APPROVED', 'REJECTED', 'HIDDEN', 'SPAM'))
);

create index idx_user_roles_role on user_roles (role);

create index idx_sites_owner_id on sites (owner_id);
create index idx_sites_domain on sites (domain);
create index idx_sites_active on sites (is_active);

create index idx_site_allowed_origins_site_id on site_allowed_origins (site_id);

create index idx_pages_site_id on pages (site_id);

create index idx_comments_page_status_created_at on comments (page_id, status, created_at);
create index idx_comments_parent_id on comments (parent_id);
create index idx_comments_author_user_id on comments (author_user_id);
create index idx_comments_moderation_queue on comments (status, created_at);

create index idx_moderation_actions_comment_id on moderation_actions (comment_id);
create index idx_moderation_actions_moderator_id on moderation_actions (moderator_id);
