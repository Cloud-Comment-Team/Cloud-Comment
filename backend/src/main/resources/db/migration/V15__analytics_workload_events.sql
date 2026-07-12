create table automod_decision_events (
    id uuid primary key default gen_random_uuid(),
    comment_id uuid not null references comments(id) on delete cascade,
    policy_version_id uuid not null references automod_policy_versions(id) on delete cascade,
    execution_mode varchar(16) not null,
    decision varchar(16) not null,
    applied_status varchar(32) not null,
    evaluated_at timestamptz not null,
    constraint automod_decision_events_execution_mode_check
        check (execution_mode in ('SHADOW', 'LIVE')),
    constraint automod_decision_events_decision_check
        check (decision in ('APPROVE', 'REVIEW', 'SPAM')),
    constraint automod_decision_events_applied_status_check
        check (applied_status in ('PENDING', 'APPROVED', 'REJECTED', 'HIDDEN', 'SPAM'))
);

insert into automod_decision_events (
    comment_id,
    policy_version_id,
    execution_mode,
    decision,
    applied_status,
    evaluated_at
)
select c.id,
       c.automod_policy_version_id,
       c.automod_execution_mode,
       c.automod_decision,
       c.automod_applied_status,
       c.automod_evaluated_at
from comments c
where c.automod_policy_version_id is not null;

create or replace function append_automod_decision_event()
returns trigger
language plpgsql
as $$
begin
    if new.automod_policy_version_id is null
        or new.automod_execution_mode is null
        or new.automod_score is null
        or new.automod_decision is null
        or new.automod_signals is null
        or new.automod_applied_status is null
        or new.automod_evaluated_at is null then
        return new;
    end if;

    if tg_op = 'UPDATE' and row(
        new.automod_policy_version_id,
        new.automod_execution_mode,
        new.automod_decision,
        new.automod_applied_status,
        new.automod_evaluated_at
    ) is not distinct from row(
        old.automod_policy_version_id,
        old.automod_execution_mode,
        old.automod_decision,
        old.automod_applied_status,
        old.automod_evaluated_at
    ) then
        return new;
    end if;

    insert into automod_decision_events (
        comment_id,
        policy_version_id,
        execution_mode,
        decision,
        applied_status,
        evaluated_at
    )
    values (
        new.id,
        new.automod_policy_version_id,
        new.automod_execution_mode,
        new.automod_decision,
        new.automod_applied_status,
        new.automod_evaluated_at
    );
    return new;
end;
$$;

create trigger trg_comments_append_automod_decision_event
    after insert or update of
        automod_policy_version_id,
        automod_execution_mode,
        automod_score,
        automod_decision,
        automod_signals,
        automod_applied_status,
        automod_evaluated_at
    on comments
    for each row execute function append_automod_decision_event();

create index idx_automod_decision_events_live_comment_time
    on automod_decision_events (comment_id, evaluated_at)
    where execution_mode = 'LIVE';

create index idx_comments_page_created_visible
    on comments (page_id, created_at)
    where deleted_at is null;

create index idx_moderation_actions_comment_created
    on moderation_actions (comment_id, created_at);
