package com.cloudcomment.site.persistence;

import com.cloudcomment.site.application.SitePage;
import com.cloudcomment.site.domain.ModerationMode;
import com.cloudcomment.site.domain.Site;
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

    @Override
    public SitePage findByOwnerId(UUID ownerId, int page, int pageSize) {
        long offset = ((long) page - 1) * pageSize;
        List<Site> items = jdbcTemplate.query(
            """
                select id, owner_id, name, domain, public_key, moderation_mode, is_active, created_at, updated_at
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
                select id, owner_id, name, domain, public_key, moderation_mode, is_active, created_at, updated_at
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
        SiteRow createdSite = jdbcTemplate.queryForObject(
            """
                insert into sites (owner_id, name, domain, public_key, moderation_mode)
                values (?, ?, ?, ?, ?)
                returning id, owner_id, name, domain, public_key, moderation_mode, is_active, created_at, updated_at
                """,
            this::mapSiteRow,
            ownerId,
            name,
            domain,
            publicKey,
            moderationMode.name()
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

        sql.append("""

            where id = ?
            returning id, owner_id, name, domain, public_key, moderation_mode, is_active, created_at, updated_at
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
            toInstant(resultSet, "created_at"),
            toInstant(resultSet, "updated_at")
        );
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
        Instant createdAt,
        Instant updatedAt
    ) {
    }
}
