create table automod_policy_versions (
    id uuid primary key default gen_random_uuid(),
    site_id uuid not null references sites(id) on delete cascade,
    version integer,
    revision integer not null default 1,
    lifecycle varchar(16) not null,
    enabled boolean not null,
    preset varchar(16) not null,
    execution_mode varchar(16) not null,
    review_threshold integer not null,
    spam_threshold integer not null,
    clean_action varchar(32) not null,
    link_action varchar(16) not null,
    max_links integer not null,
    blocked_words jsonb not null default '[]'::jsonb,
    based_on_version_id uuid,
    created_by uuid references app_users(id) on delete set null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    published_at timestamptz,
    constraint automod_policy_lifecycle_check
        check (lifecycle in ('DRAFT', 'PUBLISHED')),
    constraint automod_policy_preset_check
        check (preset in ('OPEN', 'BALANCED', 'STRICT', 'CUSTOM')),
    constraint automod_policy_execution_mode_check
        check (execution_mode in ('SHADOW', 'LIVE')),
    constraint automod_policy_clean_action_check
        check (clean_action in ('APPROVE', 'FOLLOW_SITE_MODE')),
    constraint automod_policy_link_action_check
        check (link_action in ('ALLOW', 'REVIEW', 'SPAM')),
    constraint automod_policy_thresholds_check
        check (review_threshold between 0 and 1000
            and spam_threshold between 1 and 1000
            and review_threshold < spam_threshold),
    constraint automod_policy_max_links_check
        check (max_links between 0 and 20),
    constraint automod_policy_blocked_words_check
        check (jsonb_typeof(blocked_words) = 'array'
            and jsonb_array_length(blocked_words) <= 120),
    constraint automod_policy_lifecycle_fields_check
        check (
            (lifecycle = 'DRAFT' and version is null and published_at is null)
            or
            (lifecycle = 'PUBLISHED' and version is not null and version > 0 and published_at is not null)
        ),
    constraint automod_policy_revision_check check (revision > 0),
    constraint uq_automod_policy_site_version unique (site_id, version),
    constraint uq_automod_policy_site_id unique (site_id, id),
    constraint fk_automod_policy_based_on_same_site
        foreign key (site_id, based_on_version_id)
        references automod_policy_versions(site_id, id)
        deferrable initially deferred
);

create unique index uq_automod_policy_single_draft
    on automod_policy_versions(site_id)
    where lifecycle = 'DRAFT';

create index idx_automod_policy_site_published
    on automod_policy_versions(site_id, version desc)
    where lifecycle = 'PUBLISHED';

create or replace function prevent_published_automod_policy_update()
returns trigger
language plpgsql
as $$
begin
    if old.lifecycle = 'PUBLISHED' then
        raise exception 'published automod policy versions are immutable';
    end if;
    return new;
end;
$$;

create trigger trg_automod_policy_published_immutable
    before update on automod_policy_versions
    for each row execute function prevent_published_automod_policy_update();

create table site_automod_policy_state (
    site_id uuid primary key references sites(id) on delete cascade,
    active_policy_version_id uuid not null,
    enabled boolean not null,
    execution_mode varchar(16) not null,
    last_published_version integer not null,
    legacy_settings_fingerprint varchar(64) not null,
    updated_at timestamptz not null default now(),
    constraint site_automod_state_mode_check
        check (execution_mode in ('SHADOW', 'LIVE')),
    constraint site_automod_state_version_check
        check (last_published_version > 0),
    constraint site_automod_state_fingerprint_check
        check (legacy_settings_fingerprint ~ '^[0-9a-f]{64}$'),
    constraint fk_site_automod_active_same_site
        foreign key (site_id, active_policy_version_id)
        references automod_policy_versions(site_id, id)
        deferrable initially deferred
);

alter table comments
    add column automod_policy_version_id uuid,
    add column automod_execution_mode varchar(16),
    add column automod_score integer,
    add column automod_decision varchar(16),
    add column automod_signals jsonb,
    add column automod_reason varchar(500),
    add column automod_applied_status varchar(32),
    add column automod_evaluated_at timestamptz,
    add constraint fk_comments_automod_policy_version
        foreign key (automod_policy_version_id)
        references automod_policy_versions(id)
        deferrable initially deferred,
    add constraint comments_automod_execution_mode_check
        check (automod_execution_mode is null or automod_execution_mode in ('SHADOW', 'LIVE')),
    add constraint comments_automod_score_check
        check (automod_score is null or automod_score >= 0),
    add constraint comments_automod_decision_check
        check (automod_decision is null or automod_decision in ('APPROVE', 'REVIEW', 'SPAM')),
    add constraint comments_automod_signals_check
        check (automod_signals is null or jsonb_typeof(automod_signals) = 'array'),
    add constraint comments_automod_applied_status_check
        check (automod_applied_status is null or automod_applied_status in ('PENDING', 'APPROVED', 'REJECTED', 'HIDDEN', 'SPAM')),
    add constraint comments_automod_snapshot_check
        check (
            (automod_policy_version_id is null
                and automod_execution_mode is null
                and automod_score is null
                and automod_decision is null
                and automod_signals is null
                and automod_reason is null
                and automod_applied_status is null
                and automod_evaluated_at is null)
            or
            (automod_policy_version_id is not null
                and automod_execution_mode is not null
                and automod_score is not null
                and automod_decision is not null
                and automod_signals is not null
                and automod_applied_status is not null
                and automod_evaluated_at is not null)
        );

create table automod_policy_feedback (
    id uuid primary key default gen_random_uuid(),
    comment_id uuid not null references comments(id) on delete cascade,
    policy_version_id uuid not null references automod_policy_versions(id),
    owner_id uuid not null references app_users(id) on delete cascade,
    feedback_type varchar(32) not null,
    created_at timestamptz not null default now(),
    constraint automod_policy_feedback_type_check
        check (feedback_type in ('FALSE_POSITIVE', 'FALSE_NEGATIVE')),
    constraint uq_automod_policy_feedback_comment_version unique (comment_id, policy_version_id)
);

create index idx_automod_policy_feedback_owner_created
    on automod_policy_feedback(owner_id, created_at desc);

create index idx_automod_policy_feedback_created_at
    on automod_policy_feedback(created_at);

insert into automod_policy_versions (
    site_id,
    version,
    revision,
    lifecycle,
    enabled,
    preset,
    execution_mode,
    review_threshold,
    spam_threshold,
    clean_action,
    link_action,
    max_links,
    blocked_words,
    created_by,
    published_at
)
select s.id,
       1,
       1,
       'PUBLISHED',
       (s.automod_enabled and s.automod_strictness <> 'OFF'),
       case
           when btrim(s.automod_blocked_words) <> ''
               or s.automod_block_links
               or not s.automod_hold_links
               or s.automod_max_links <> 2
               then 'CUSTOM'
           when s.automod_strictness = 'RELAXED' then 'OPEN'
           when s.automod_strictness = 'STRICT' then 'STRICT'
           else 'BALANCED'
       end,
       'LIVE',
       case s.automod_strictness
           when 'RELAXED' then 70
           when 'STRICT' then 25
           else 45
       end,
       case s.automod_strictness
           when 'RELAXED' then 130
           when 'STRICT' then 85
           else 90
       end,
       case when s.automod_strictness = 'STRICT' then 'FOLLOW_SITE_MODE' else 'APPROVE' end,
       case
           when s.automod_block_links then 'SPAM'
           when s.automod_hold_links then 'REVIEW'
           else 'ALLOW'
       end,
       case when s.automod_strictness = 'STRICT' and s.automod_hold_links then 0 else s.automod_max_links end,
       coalesce(
           (
               select jsonb_agg(btrim(word) order by ordinal)
               from unnest(regexp_split_to_array(s.automod_blocked_words, E'\\r?\\n'))
                    with ordinality as words(word, ordinal)
               where btrim(word) <> '' and ordinal <= 120
           ),
           '[]'::jsonb
       ),
       s.owner_id,
       now()
from sites s;

insert into site_automod_policy_state (
    site_id,
    active_policy_version_id,
    enabled,
    execution_mode,
    last_published_version,
    legacy_settings_fingerprint
)
select p.site_id,
       p.id,
       p.enabled,
       p.execution_mode,
       1,
       encode(digest(
           s.automod_enabled::text || chr(31)
           || s.automod_strictness || chr(31)
           || s.automod_blocked_words || chr(31)
           || s.automod_hold_links::text || chr(31)
           || s.automod_block_links::text || chr(31)
           || s.automod_max_links::text,
           'sha256'
       ), 'hex')
from automod_policy_versions p
join sites s on s.id = p.site_id
where p.lifecycle = 'PUBLISHED' and p.version = 1;
