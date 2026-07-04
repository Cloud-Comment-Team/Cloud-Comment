alter table sites
    add column widget_theme varchar(16) not null default 'AUTO',
    add column widget_accent_color varchar(7) not null default '#0f766e',
    add column widget_corner_radius varchar(16) not null default 'MEDIUM',
    add constraint chk_sites_widget_theme check (widget_theme in ('AUTO', 'LIGHT', 'DARK')),
    add constraint chk_sites_widget_accent_color check (widget_accent_color ~ '^#[0-9A-Fa-f]{6}$'),
    add constraint chk_sites_widget_corner_radius check (widget_corner_radius in ('SMALL', 'MEDIUM', 'LARGE'));

create table comment_reactions (
    id uuid primary key default gen_random_uuid(),
    comment_id uuid not null references comments (id) on delete cascade,
    user_id uuid not null references app_users (id) on delete cascade,
    reaction_type varchar(24) not null,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    constraint uq_comment_reactions_comment_user unique (comment_id, user_id),
    constraint chk_comment_reactions_type check (reaction_type in ('LIKE', 'LOVE', 'LAUGH', 'WOW'))
);

create index idx_comment_reactions_comment_id on comment_reactions (comment_id);
create index idx_comment_reactions_user_id on comment_reactions (user_id);
