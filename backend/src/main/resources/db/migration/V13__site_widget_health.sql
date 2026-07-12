create table site_widget_health (
    site_id uuid primary key references sites (id) on delete cascade,
    last_successful_origin varchar(512),
    last_successful_at timestamp with time zone,
    last_rejected_origin varchar(512),
    last_rejected_at timestamp with time zone,
    constraint chk_site_widget_health_success_pair check (
        (last_successful_origin is null) = (last_successful_at is null)
    ),
    constraint chk_site_widget_health_rejected_pair check (
        (last_rejected_origin is null) = (last_rejected_at is null)
    )
);

create index idx_site_widget_health_rejected_at
    on site_widget_health (last_rejected_at)
    where last_rejected_at is not null;
