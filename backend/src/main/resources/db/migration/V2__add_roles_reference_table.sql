create table roles (
    name varchar(32) primary key,
    description varchar(255) not null,
    created_at timestamp with time zone not null default now(),
    constraint chk_roles_name check (name in ('OWNER', 'COMMENTER', 'MODERATOR')),
    constraint chk_roles_description_not_blank check (btrim(description) <> '')
);

insert into roles (name, description)
values
    ('OWNER', 'Site owner with administrative access to owned resources'),
    ('COMMENTER', 'Authenticated visitor who can publish comments'),
    ('MODERATOR', 'Moderator role reserved for future moderation flows');

alter table user_roles
    add constraint fk_user_roles_role foreign key (role)
        references roles (name);
