alter table sites
    add column automod_enabled boolean not null default true,
    add column automod_strictness varchar(16) not null default 'BALANCED',
    add column automod_blocked_words text not null default '',
    add column automod_hold_links boolean not null default true,
    add column automod_block_links boolean not null default false,
    add column automod_max_links integer not null default 2;

alter table sites
    add constraint sites_automod_strictness_check
        check (automod_strictness in ('OFF', 'RELAXED', 'BALANCED', 'STRICT')),
    add constraint sites_automod_max_links_check
        check (automod_max_links between 0 and 20);

alter table comments
    add column edited_at timestamptz,
    add column deleted_at timestamptz,
    add column deleted_by_author boolean not null default false;

create index idx_comments_author_visible
    on comments (author_user_id, created_at desc)
    where deleted_at is null;

create index idx_comments_page_public_visible
    on comments (page_id, status, created_at asc)
    where deleted_at is null;
