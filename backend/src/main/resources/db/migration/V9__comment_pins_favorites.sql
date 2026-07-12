alter table comments
    add column is_pinned boolean not null default false,
    add column is_favorite boolean not null default false;

create index idx_comments_page_public_pinned
    on comments (page_id, is_pinned desc, created_at desc)
    where deleted_at is null and status = 'APPROVED' and parent_id is null;

create index idx_comments_moderation_favorite
    on comments (is_favorite, updated_at desc)
    where deleted_at is null;
