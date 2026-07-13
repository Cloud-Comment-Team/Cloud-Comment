alter table auth_sessions
    add column audience varchar(16) not null default 'LEGACY';

alter table auth_sessions
    add constraint chk_auth_sessions_audience
        check (audience in ('ADMIN', 'WIDGET', 'LEGACY'));

update auth_sessions
set revoked_at = greatest(now(), created_at)
where revoked_at is null
  and expires_at > now();

create index idx_auth_sessions_active_audience
    on auth_sessions (audience, expires_at)
    where revoked_at is null;
