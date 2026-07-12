alter table sites
    add column widget_style_version integer not null default 2,
    add column widget_style_config jsonb;

update sites
set widget_style_config = jsonb_build_object(
    'version', 2,
    'theme', widget_theme,
    'accentColor', widget_accent_color,
    'cornerRadius', widget_corner_radius,
    'density', 'COMFORTABLE',
    'contentWidth', 'READABLE',
    'alignment', 'CENTER',
    'fontScale', 'MEDIUM',
    'fontFamily', 'INHERIT',
    'showHeader', true,
    'headerTitle', 'Комментарии',
    'composerPosition', 'BOTTOM',
    'defaultSort', 'PINNED_FIRST',
    'showSort', true,
    'enabledReactions', jsonb_build_array('LIKE', 'LOVE', 'LAUGH', 'WOW'),
    'avatarStyle', 'INITIALS',
    'elevation', 'BORDER',
    'locale', 'RU',
    'commentsTitle', 'Комментарии',
    'composerPlaceholder', 'Напишите комментарий',
    'emptyMessage', 'Пока нет комментариев. Будьте первым, кто начнет обсуждение.'
);

create function sync_widget_style_config_v2() returns trigger as $$
begin
    if new.widget_style_config is null then
        new.widget_style_config = jsonb_build_object(
            'version', 2,
            'theme', new.widget_theme,
            'accentColor', new.widget_accent_color,
            'cornerRadius', new.widget_corner_radius,
            'density', 'COMFORTABLE',
            'contentWidth', 'READABLE',
            'alignment', 'CENTER',
            'fontScale', 'MEDIUM',
            'fontFamily', 'INHERIT',
            'showHeader', true,
            'headerTitle', 'Комментарии',
            'composerPosition', 'BOTTOM',
            'defaultSort', 'PINNED_FIRST',
            'showSort', true,
            'enabledReactions', jsonb_build_array('LIKE', 'LOVE', 'LAUGH', 'WOW'),
            'avatarStyle', 'INITIALS',
            'elevation', 'BORDER',
            'locale', 'RU',
            'commentsTitle', 'Комментарии',
            'composerPlaceholder', 'Напишите комментарий',
            'emptyMessage', 'Пока нет комментариев. Будьте первым, кто начнет обсуждение.'
        );
    elsif tg_op = 'UPDATE'
        and new.widget_style_config is not distinct from old.widget_style_config
        and (new.widget_theme, new.widget_accent_color, new.widget_corner_radius)
            is distinct from (old.widget_theme, old.widget_accent_color, old.widget_corner_radius) then
        new.widget_style_config = jsonb_set(
            jsonb_set(
                jsonb_set(new.widget_style_config, '{theme}', to_jsonb(new.widget_theme)),
                '{accentColor}', to_jsonb(new.widget_accent_color)
            ),
            '{cornerRadius}', to_jsonb(new.widget_corner_radius)
        );
    end if;
    new.widget_style_version = greatest(coalesce(new.widget_style_version, 2), 2);
    return new;
end;
$$ language plpgsql;

create trigger trg_sync_widget_style_config_v2
    before insert or update of widget_theme, widget_accent_color, widget_corner_radius, widget_style_config
    on sites
    for each row execute function sync_widget_style_config_v2();

alter table sites
    alter column widget_style_config set not null,
    add constraint chk_sites_widget_style_version check (widget_style_version >= 2),
    add constraint chk_sites_widget_style_config_object check (jsonb_typeof(widget_style_config) = 'object');
