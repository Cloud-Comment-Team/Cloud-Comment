create table privacy_events (
    id uuid primary key default gen_random_uuid(),
    user_id uuid references app_users (id) on delete set null,
    event_type varchar(64) not null,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamp with time zone not null default now(),
    constraint chk_privacy_events_event_type check (
        event_type in (
            'CONSENT_ACCEPTED',
            'ACCOUNT_DELETION_REQUESTED',
            'ACCOUNT_DELETION_CONFIRMED',
            'ACCOUNT_DELETED',
            'PERSONAL_DATA_EXPORTED',
            'RETENTION_CLEANUP_COMPLETED'
        )
    )
);

create index idx_privacy_events_user_id_created_at
    on privacy_events (user_id, created_at desc);

create index idx_privacy_events_event_type_created_at
    on privacy_events (event_type, created_at desc);
