package com.cloudcomment.site.persistence;

import tools.jackson.databind.ObjectMapper;
import com.cloudcomment.site.application.SitePage;
import com.cloudcomment.site.domain.AutoModerationSettings;
import com.cloudcomment.site.domain.AutoModerationStrictness;
import com.cloudcomment.site.domain.ModerationMode;
import com.cloudcomment.site.domain.Site;
import com.cloudcomment.site.domain.WidgetCornerRadius;
import com.cloudcomment.site.domain.WidgetStyle;
import com.cloudcomment.site.domain.WidgetTheme;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
class JdbcSiteRepository implements SiteRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public SitePage findByOwnerId(UUID ownerId, int page, int pageSize) {
        long offset = ((long) page - 1) * pageSize;
        List<Site> items = jdbcTemplate.query(
            """
                select id, owner_id, name, domain, public_key, moderation_mode, is_active,
                       widget_theme, widget_accent_color, widget_corner_radius, widget_style_version, widget_style_config,
                       automod_enabled, automod_strictness, automod_blocked_words,
                       automod_hold_links, automod_block_links, automod_max_links,
                       created_at, updated_at
                from sites
                where owner_id = ?
                order by created_at desc, id desc
                limit ? offset ?
                """,
            this::mapSiteRow,
            ownerId,
            pageSize,
            offset
        ).stream().map(this::toSite).toList();

        Long totalItems = jdbcTemplate.queryForObject(
            "select count(*) from sites where owner_id = ?",
            Long.class,
            ownerId
        );

        return new SitePage(items, page, pageSize, Objects.requireNonNullElse(totalItems, 0L));
    }

    @Override
    public Optional<Site> findById(UUID siteId) {
        List<SiteRow> rows = jdbcTemplate.query(
            """
                select id, owner_id, name, domain, public_key, moderation_mode, is_active,
                       widget_theme, widget_accent_color, widget_corner_radius, widget_style_version, widget_style_config,
                       automod_enabled, automod_strictness, automod_blocked_words,
                       automod_hold_links, automod_block_links, automod_max_links,
                       created_at, updated_at
                from sites
                where id = ?
                """,
            this::mapSiteRow,
            siteId
        );
        return rows.stream().findFirst().map(this::toSite);
    }

    @Override
    @Transactional
    public Site create(
        UUID ownerId,
        String name,
        String domain,
        String publicKey,
        ModerationMode moderationMode,
        List<String> allowedOrigins
    ) {
        return create(
            ownerId,
            name,
            domain,
            publicKey,
            moderationMode,
            WidgetStyle.defaultStyle(),
            allowedOrigins
        );
    }

    @Override
    @Transactional
    public Site create(
        UUID ownerId,
        String name,
        String domain,
        String publicKey,
        ModerationMode moderationMode,
        WidgetStyle widgetStyle,
        List<String> allowedOrigins
    ) {
        return create(
            ownerId,
            name,
            domain,
            publicKey,
            moderationMode,
            widgetStyle,
            AutoModerationSettings.defaultSettings(),
            allowedOrigins
        );
    }

    @Override
    @Transactional
    public Site create(
        UUID ownerId,
        String name,
        String domain,
        String publicKey,
        ModerationMode moderationMode,
        WidgetStyle widgetStyle,
        AutoModerationSettings autoModeration,
        List<String> allowedOrigins
    ) {
        WidgetStyle normalizedStyle = widgetStyle != null ? widgetStyle : WidgetStyle.defaultStyle();
        AutoModerationSettings normalizedAutoModeration = autoModeration != null
            ? autoModeration
            : AutoModerationSettings.defaultSettings();
        SiteRow createdSite = jdbcTemplate.queryForObject(
            """
                insert into sites (
                    owner_id,
                    name,
                    domain,
                    public_key,
                    moderation_mode,
                    widget_theme,
                    widget_accent_color,
                    widget_corner_radius,
                    widget_style_version,
                    widget_style_config,
                    automod_enabled,
                    automod_strictness,
                    automod_blocked_words,
                    automod_hold_links,
                    automod_block_links,
                    automod_max_links
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)
                returning id, owner_id, name, domain, public_key, moderation_mode, is_active,
                          widget_theme, widget_accent_color, widget_corner_radius, widget_style_version, widget_style_config,
                          automod_enabled, automod_strictness, automod_blocked_words,
                          automod_hold_links, automod_block_links, automod_max_links,
                          created_at, updated_at
                """,
            this::mapSiteRow,
            ownerId,
            name,
            domain,
            publicKey,
            moderationMode.name(),
            normalizedStyle.theme().name(),
            normalizedStyle.accentColor(),
            normalizedStyle.cornerRadius().name(),
            normalizedStyle.version(),
            serializeWidgetStyle(normalizedStyle),
            normalizedAutoModeration.enabled(),
            normalizedAutoModeration.strictness().name(),
            serializeBlockedWords(normalizedAutoModeration.blockedWords()),
            normalizedAutoModeration.holdLinks(),
            normalizedAutoModeration.blockLinks(),
            normalizedAutoModeration.maxLinks()
        );
        SiteRow site = Objects.requireNonNull(createdSite, "created site must not be null");
        insertAllowedOrigins(site.id(), allowedOrigins);
        return toSite(site);
    }

    @Override
    @Transactional
    public Optional<Site> update(UUID siteId, SiteUpdate update) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            update sites
            set updated_at = now()
            """);

        if (update.name() != null) {
            sql.append(", name = ?");
            params.add(update.name());
        }
        if (update.domain() != null) {
            sql.append(", domain = ?");
            params.add(update.domain());
        }
        if (update.moderationMode() != null) {
            sql.append(", moderation_mode = ?");
            params.add(update.moderationMode().name());
        }
        if (update.active() != null) {
            sql.append(", is_active = ?");
            params.add(update.active());
        }
        if (update.widgetStyle() != null) {
            sql.append(", widget_theme = ?, widget_accent_color = ?, widget_corner_radius = ?, widget_style_version = ?, widget_style_config = ?::jsonb");
            params.add(update.widgetStyle().theme().name());
            params.add(update.widgetStyle().accentColor());
            params.add(update.widgetStyle().cornerRadius().name());
            params.add(update.widgetStyle().version());
            params.add(serializeWidgetStyle(update.widgetStyle()));
        }
        if (update.autoModeration() != null) {
            sql.append("""
                , automod_enabled = ?,
                  automod_strictness = ?,
                  automod_blocked_words = ?,
                  automod_hold_links = ?,
                  automod_block_links = ?,
                  automod_max_links = ?
                """);
            params.add(update.autoModeration().enabled());
            params.add(update.autoModeration().strictness().name());
            params.add(serializeBlockedWords(update.autoModeration().blockedWords()));
            params.add(update.autoModeration().holdLinks());
            params.add(update.autoModeration().blockLinks());
            params.add(update.autoModeration().maxLinks());
        }

        sql.append("""

            where id = ?
            returning id, owner_id, name, domain, public_key, moderation_mode, is_active,
                      widget_theme, widget_accent_color, widget_corner_radius, widget_style_version, widget_style_config,
                      automod_enabled, automod_strictness, automod_blocked_words,
                      automod_hold_links, automod_block_links, automod_max_links,
                      created_at, updated_at
            """);
        params.add(siteId);

        List<SiteRow> rows = jdbcTemplate.query(sql.toString(), this::mapSiteRow, params.toArray());
        return rows.stream().findFirst().map(this::toSite);
    }

    @Override
    @Transactional
    public Optional<Site> replaceAllowedOrigins(UUID siteId, List<String> allowedOrigins) {
        if (!existsById(siteId)) {
            return Optional.empty();
        }

        jdbcTemplate.update("delete from site_allowed_origins where site_id = ?", siteId);
        insertAllowedOrigins(siteId, allowedOrigins);
        jdbcTemplate.update("update sites set updated_at = now() where id = ?", siteId);
        return findById(siteId);
    }

    @Override
    public boolean existsByOwnerIdAndDomain(UUID ownerId, String domain) {
        Boolean exists = jdbcTemplate.queryForObject(
            "select exists(select 1 from sites where owner_id = ? and domain = ?)",
            Boolean.class,
            ownerId,
            domain
        );
        return Boolean.TRUE.equals(exists);
    }

    @Override
    @Transactional
    public boolean deleteById(UUID siteId) {
        return jdbcTemplate.update("delete from sites where id = ?", siteId) > 0;
    }

    @Override
    public boolean existsByOwnerIdAndDomainExcludingSite(UUID ownerId, String domain, UUID siteId) {
        Boolean exists = jdbcTemplate.queryForObject(
            "select exists(select 1 from sites where owner_id = ? and domain = ? and id <> ?)",
            Boolean.class,
            ownerId,
            domain,
            siteId
        );
        return Boolean.TRUE.equals(exists);
    }

    private boolean existsById(UUID siteId) {
        Boolean exists = jdbcTemplate.queryForObject(
            "select exists(select 1 from sites where id = ?)",
            Boolean.class,
            siteId
        );
        return Boolean.TRUE.equals(exists);
    }

    private void insertAllowedOrigins(UUID siteId, List<String> allowedOrigins) {
        for (String origin : allowedOrigins) {
            jdbcTemplate.update(
                """
                    insert into site_allowed_origins (site_id, origin)
                    values (?, ?)
                    """,
                siteId,
                origin
            );
        }
    }

    private Site toSite(SiteRow row) {
        return new Site(
            row.id(),
            row.ownerId(),
            row.name(),
            row.domain(),
            row.publicKey(),
            row.moderationMode(),
            row.active(),
            row.widgetStyle(),
            row.autoModeration(),
            findAllowedOrigins(row.id()),
            row.createdAt(),
            row.updatedAt()
        );
    }

    private List<String> findAllowedOrigins(UUID siteId) {
        return jdbcTemplate.queryForList(
            """
                select origin
                from site_allowed_origins
                where site_id = ?
                order by origin
                """,
            String.class,
            siteId
        );
    }

    private SiteRow mapSiteRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new SiteRow(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("owner_id", UUID.class),
            resultSet.getString("name"),
            resultSet.getString("domain"),
            resultSet.getString("public_key"),
            ModerationMode.valueOf(resultSet.getString("moderation_mode")),
            resultSet.getBoolean("is_active"),
            deserializeWidgetStyle(resultSet),
            new AutoModerationSettings(
                resultSet.getBoolean("automod_enabled"),
                AutoModerationStrictness.valueOf(resultSet.getString("automod_strictness")),
                parseBlockedWords(resultSet.getString("automod_blocked_words")),
                resultSet.getBoolean("automod_hold_links"),
                resultSet.getBoolean("automod_block_links"),
                resultSet.getInt("automod_max_links")
            ),
            toInstant(resultSet, "created_at"),
            toInstant(resultSet, "updated_at")
        );
    }

    private String serializeBlockedWords(List<String> blockedWords) {
        return String.join("\n", blockedWords);
    }

    private String serializeWidgetStyle(WidgetStyle style) {
        try {
            return objectMapper.writeValueAsString(style);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("failed to serialize widget style", exception);
        }
    }

    private WidgetStyle deserializeWidgetStyle(ResultSet resultSet) throws SQLException {
        String json = resultSet.getString("widget_style_config");
        if (json != null && !json.isBlank()) {
            try {
                return objectMapper.readValue(json, WidgetStyle.class);
            } catch (RuntimeException exception) {
                throw new SQLException("failed to deserialize widget style", exception);
            }
        }
        return new WidgetStyle(
            WidgetTheme.valueOf(resultSet.getString("widget_theme")),
            resultSet.getString("widget_accent_color"),
            WidgetCornerRadius.valueOf(resultSet.getString("widget_corner_radius"))
        );
    }

    private List<String> parseBlockedWords(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return value.lines()
            .map(String::trim)
            .filter(word -> !word.isBlank())
            .toList();
    }

    private Instant toInstant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    private record SiteRow(
        UUID id,
        UUID ownerId,
        String name,
        String domain,
        String publicKey,
        ModerationMode moderationMode,
        boolean active,
        WidgetStyle widgetStyle,
        AutoModerationSettings autoModeration,
        Instant createdAt,
        Instant updatedAt
    ) {
    }
}
