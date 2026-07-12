package com.cloudcomment.site.persistence;

import com.cloudcomment.site.domain.SiteWidgetHealth;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
class JdbcSiteWidgetHealthRepository implements SiteWidgetHealthRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void recordSuccessfulOrigin(UUID siteId, String origin, Instant occurredAt) {
        jdbcTemplate.update(
            """
                insert into site_widget_health (site_id, last_successful_origin, last_successful_at)
                select id, ?, ? from sites where id = ?
                on conflict (site_id) do update
                    set last_successful_origin = excluded.last_successful_origin,
                        last_successful_at = excluded.last_successful_at
                    where site_widget_health.last_successful_at is null
                       or site_widget_health.last_successful_at <= excluded.last_successful_at
                """,
            origin,
            toOffsetDateTime(occurredAt),
            siteId
        );
    }

    @Override
    @Transactional
    public void recordRejectedOrigin(UUID siteId, String origin, Instant occurredAt) {
        jdbcTemplate.update(
            """
                insert into site_widget_health (site_id, last_rejected_origin, last_rejected_at)
                select id, ?, ? from sites where id = ?
                on conflict (site_id) do update
                    set last_rejected_origin = excluded.last_rejected_origin,
                        last_rejected_at = excluded.last_rejected_at
                    where site_widget_health.last_rejected_at is null
                       or site_widget_health.last_rejected_at <= excluded.last_rejected_at
                """,
            origin,
            toOffsetDateTime(occurredAt),
            siteId
        );
    }

    @Override
    public Optional<SiteWidgetHealth> findBySiteId(UUID siteId) {
        return jdbcTemplate.query(
            """
                select site_id, last_successful_origin, last_successful_at,
                       last_rejected_origin, last_rejected_at
                from site_widget_health
                where site_id = ?
                """,
            this::mapHealth,
            siteId
        ).stream().findFirst();
    }

    @Override
    public boolean hasComments(UUID siteId) {
        Boolean exists = jdbcTemplate.queryForObject(
            """
                select exists(
                    select 1 from comments c
                    join pages p on p.id = c.page_id
                    where p.site_id = ?
                )
                """,
            Boolean.class,
            siteId
        );
        return Boolean.TRUE.equals(exists);
    }

    @Override
    @Transactional
    public int clearRejectedBefore(Instant threshold) {
        return jdbcTemplate.update(
            """
                update site_widget_health
                set last_rejected_origin = null, last_rejected_at = null
                where last_rejected_at < ?
                """,
            toOffsetDateTime(threshold)
        );
    }

    private SiteWidgetHealth mapHealth(ResultSet resultSet, int rowNumber) throws SQLException {
        return new SiteWidgetHealth(
            resultSet.getObject("site_id", UUID.class),
            resultSet.getString("last_successful_origin"),
            toInstant(resultSet, "last_successful_at"),
            resultSet.getString("last_rejected_origin"),
            toInstant(resultSet, "last_rejected_at")
        );
    }

    private Instant toInstant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private OffsetDateTime toOffsetDateTime(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }
}
