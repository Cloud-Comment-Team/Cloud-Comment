alter table comments
    add column author_kind varchar(16) not null default 'VISITOR',
    add column owner_reply_operation_id uuid,
    add constraint chk_comments_author_kind check (author_kind in ('VISITOR', 'OWNER'));

create unique index uq_comments_owner_reply_operation
    on comments (author_user_id, owner_reply_operation_id)
    where owner_reply_operation_id is not null;
