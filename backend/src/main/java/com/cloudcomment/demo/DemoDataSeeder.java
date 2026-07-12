package com.cloudcomment.demo;

import com.cloudcomment.site.domain.SiteInputRules;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class DemoDataSeeder implements ApplicationRunner {

    private static final UUID DEMO_OWNER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID DEMO_PAGE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID DEMO_COMMENT_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID DEMO_REPLY_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final String DEMO_PUBLIC_KEY =
        "c680d24688764049975c73ccf029408fc680d24688764049975c73ccf029408f";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final DemoDataProperties properties;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            UUID ownerId = upsertOwner();
            upsertSite(ownerId);
            upsertAllowedOrigins();
            upsertPage();
            upsertComments(ownerId);
        });
    }

    private UUID upsertOwner() {
        return Objects.requireNonNull(jdbcTemplate.queryForObject(
            """
                insert into app_users (id, email, password_hash, display_name, is_enabled)
                values (?, ?, ?, ?, true)
                on conflict (email) do update
                set password_hash = excluded.password_hash,
                    display_name = excluded.display_name,
                    is_enabled = true,
                    deleted_at = null,
                    updated_at = now()
                returning id
                """,
            UUID.class,
            DEMO_OWNER_ID,
            properties.ownerEmail(),
            passwordEncoder.encode(properties.ownerPassword()),
            "Demo Owner"
        ));
    }

    private void upsertSite(UUID ownerId) {
        jdbcTemplate.update(
            """
                insert into sites (
                    id,
                    owner_id,
                    name,
                    domain,
                    public_key,
                    moderation_mode,
                    is_active,
                    widget_theme,
                    widget_accent_color,
                    widget_corner_radius,
                    automod_enabled,
                    automod_strictness,
                    automod_blocked_words,
                    automod_hold_links,
                    automod_block_links,
                    automod_max_links
                )
                values (?, ?, ?, ?, ?, 'PRE_MODERATION', true, 'AUTO', '#0f766e', 'MEDIUM',
                        true, 'BALANCED', '', true, false, 2)
                on conflict (id) do update
                set owner_id = excluded.owner_id,
                    name = excluded.name,
                    domain = excluded.domain,
                    public_key = excluded.public_key,
                    is_active = true,
                    updated_at = now()
                """,
            properties.siteId(),
            ownerId,
            "Demo site",
            "localhost",
            DEMO_PUBLIC_KEY
        );

        jdbcTemplate.update(
            "insert into user_roles (user_id, role) values (?, 'OWNER') on conflict do nothing",
            ownerId
        );
        jdbcTemplate.update(
            "insert into user_roles (user_id, role) values (?, 'COMMENTER') on conflict do nothing",
            ownerId
        );
    }

    private void upsertAllowedOrigins() {
        List<String> origins = SiteInputRules.normalizeOrigins(properties.allowedOrigins());
        if (origins.isEmpty()) {
            throw new IllegalStateException("Demo allowed origins are invalid");
        }

        for (String origin : origins) {
            jdbcTemplate.update(
                """
                    insert into site_allowed_origins (site_id, origin)
                    values (?, ?)
                    on conflict (site_id, origin) do nothing
                    """,
                properties.siteId(),
                origin
            );
        }
    }

    private void upsertPage() {
        jdbcTemplate.update(
            """
                insert into pages (id, site_id, url, title)
                values (?, ?, ?, ?)
                on conflict (id) do update
                set site_id = excluded.site_id,
                    url = excluded.url,
                    title = excluded.title,
                    updated_at = now()
                """,
            DEMO_PAGE_ID,
            properties.siteId(),
            properties.pageUrl(),
            "Demo page"
        );
    }

    private void upsertComments(UUID ownerId) {
        OffsetDateTime rootCreatedAt = OffsetDateTime.parse("2026-07-05T10:00:00+03:00");
        OffsetDateTime replyCreatedAt = OffsetDateTime.parse("2026-07-05T10:08:00+03:00");

        jdbcTemplate.update(
            """
                insert into comments (
                    id,
                    page_id,
                    author_user_id,
                    author_name,
                    author_email,
                    body,
                    status,
                    created_at,
                    updated_at
                )
                values (?, ?, ?, ?, ?, ?, 'APPROVED', ?, ?)
                on conflict (id) do update
                set page_id = excluded.page_id,
                    author_user_id = excluded.author_user_id,
                    author_name = excluded.author_name,
                    author_email = excluded.author_email,
                    body = excluded.body,
                    status = 'APPROVED',
                    deleted_at = null,
                    deleted_by_author = false,
                    updated_at = excluded.updated_at
                """,
            DEMO_COMMENT_ID,
            DEMO_PAGE_ID,
            ownerId,
            "Demo Owner",
            properties.ownerEmail(),
            "Привет! Это демо-комментарий, который появляется сразу после локального запуска.",
            rootCreatedAt,
            rootCreatedAt
        );

        jdbcTemplate.update(
            """
                insert into comments (
                    id,
                    page_id,
                    parent_id,
                    author_user_id,
                    author_name,
                    author_email,
                    body,
                    status,
                    created_at,
                    updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, 'APPROVED', ?, ?)
                on conflict (id) do update
                set page_id = excluded.page_id,
                    parent_id = excluded.parent_id,
                    author_user_id = excluded.author_user_id,
                    author_name = excluded.author_name,
                    author_email = excluded.author_email,
                    body = excluded.body,
                    status = 'APPROVED',
                    deleted_at = null,
                    deleted_by_author = false,
                    updated_at = excluded.updated_at
                """,
            DEMO_REPLY_ID,
            DEMO_PAGE_ID,
            DEMO_COMMENT_ID,
            ownerId,
            "Demo Owner",
            properties.ownerEmail(),
            "А это ответ на комментарий, чтобы было видно дерево обсуждения.",
            replyCreatedAt,
            replyCreatedAt
        );
    }
}
