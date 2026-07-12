alter table moderation_actions
    add column operation_id uuid,
    add column reverts_action_id uuid references moderation_actions(id);

alter table moderation_actions drop constraint chk_moderation_actions_action;
alter table moderation_actions
    add constraint chk_moderation_actions_action
        check (action in ('APPROVE', 'REJECT', 'HIDE', 'MARK_SPAM', 'RESTORE', 'UNDO')),
    add constraint uq_moderation_actions_comment_operation unique (comment_id, operation_id);

create index idx_moderation_actions_reverts_action_id
    on moderation_actions (reverts_action_id) where reverts_action_id is not null;

create unique index uq_moderation_actions_single_undo
    on moderation_actions (reverts_action_id) where reverts_action_id is not null;
