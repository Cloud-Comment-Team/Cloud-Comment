create table owner_notifications (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references app_users (id) on delete cascade,
    comment_id uuid not null references comments (id) on delete cascade,
    deduplication_key varchar(160) not null,
    read_at timestamp with time zone,
    created_at timestamp with time zone not null default now(),
    constraint uq_owner_notifications_deduplication unique (owner_id, deduplication_key)
);

create index idx_owner_notifications_owner_created
    on owner_notifications (owner_id, created_at desc, id desc);

create index idx_owner_notifications_owner_unread
    on owner_notifications (owner_id, created_at desc)
    where read_at is null;

create index idx_owner_notifications_created_at
    on owner_notifications (created_at);
