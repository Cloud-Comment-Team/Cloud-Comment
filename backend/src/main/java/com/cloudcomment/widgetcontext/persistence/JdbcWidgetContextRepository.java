package com.cloudcomment.widgetcontext.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcWidgetContextRepository implements WidgetContextRepository {

    private final JdbcTemplate jdbcTemplate;

    JdbcWidgetContextRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void createBootstrapTicket(
        String ticketHash,
        UUID siteId,
        String origin,
        String canonicalPageUrl,
        String pageUrlHash,
        String publicKeyFingerprint,
        byte[] publicKeySpki,
        Instant createdAt,
        Instant expiresAt
    ) {
        jdbcTemplate.update(
            """
                delete from widget_bootstrap_tickets
                where site_id = ?
                  and origin = ?
                  and public_key_fingerprint = ?
                  and consumed_at is null
                  and expires_at <= ?
                """,
            siteId,
            origin,
            publicKeyFingerprint,
            toOffsetDateTime(createdAt)
        );
        jdbcTemplate.update(
            """
                insert into widget_bootstrap_tickets (
                    ticket_hash, site_id, origin, canonical_page_url, page_url_hash,
                    public_key_fingerprint, public_key_spki, created_at, expires_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            ticketHash,
            siteId,
            origin,
            canonicalPageUrl,
            pageUrlHash,
            publicKeyFingerprint,
            publicKeySpki,
            toOffsetDateTime(createdAt),
            toOffsetDateTime(expiresAt)
        );
    }

    @Override
    public Optional<StoredWidgetBootstrapTicket> findActiveBootstrapTicket(
        String ticketHash,
        UUID siteId,
        Instant now
    ) {
        List<StoredWidgetBootstrapTicket> rows = jdbcTemplate.query(
            """
                select site_id, origin, canonical_page_url, page_url_hash,
                       public_key_fingerprint, public_key_spki, expires_at
                from widget_bootstrap_tickets
                where ticket_hash = ?
                  and site_id = ?
                  and consumed_at is null
                  and expires_at > ?
                """,
            this::mapBootstrapTicket,
            ticketHash,
            siteId,
            toOffsetDateTime(now)
        );
        return rows.stream().findFirst();
    }

    @Override
    public boolean consumeBootstrapTicket(String ticketHash, UUID siteId, Instant consumedAt) {
        return jdbcTemplate.update(
            """
                update widget_bootstrap_tickets
                set consumed_at = greatest(?, created_at)
                where ticket_hash = ?
                  and site_id = ?
                  and consumed_at is null
                  and expires_at > ?
                """,
            toOffsetDateTime(consumedAt),
            ticketHash,
            siteId,
            toOffsetDateTime(consumedAt)
        ) == 1;
    }

    @Override
    public void deleteBootstrapTicket(String ticketHash, UUID siteId) {
        jdbcTemplate.update(
            "delete from widget_bootstrap_tickets where ticket_hash = ? and site_id = ?",
            ticketHash,
            siteId
        );
    }

    @Override
    public void createFrameContext(
        String tokenHash,
        UUID siteId,
        String origin,
        String pageUrlHash,
        Instant createdAt,
        Instant expiresAt
    ) {
        jdbcTemplate.update(
            """
                insert into widget_frame_contexts (
                    token_hash, site_id, origin, page_url_hash, created_at, expires_at
                ) values (?, ?, ?, ?, ?, ?)
                """,
            tokenHash,
            siteId,
            origin,
            pageUrlHash,
            toOffsetDateTime(createdAt),
            toOffsetDateTime(expiresAt)
        );
    }

    @Override
    public Optional<StoredWidgetFrameContext> findActiveFrameContext(
        String tokenHash,
        UUID siteId,
        Instant now
    ) {
        List<StoredWidgetFrameContext> rows = jdbcTemplate.query(
            """
                select id, site_id, origin, page_url_hash, expires_at
                from widget_frame_contexts
                where token_hash = ?
                  and site_id = ?
                  and expires_at > ?
                """,
            this::mapFrameContext,
            tokenHash,
            siteId,
            toOffsetDateTime(now)
        );
        return rows.stream().findFirst();
    }

    @Override
    public int deleteExpiredBootstrapTickets(Instant cutoff) {
        int consumed = jdbcTemplate.update(
            "delete from widget_bootstrap_tickets where consumed_at is not null"
        );
        int expired = jdbcTemplate.update(
            "delete from widget_bootstrap_tickets where expires_at <= ?",
            toOffsetDateTime(cutoff)
        );
        return consumed + expired;
    }

    @Override
    public int deleteExpiredFrameContexts(Instant cutoff) {
        return jdbcTemplate.update(
            "delete from widget_frame_contexts where expires_at <= ?",
            toOffsetDateTime(cutoff)
        );
    }

    private StoredWidgetBootstrapTicket mapBootstrapTicket(ResultSet resultSet, int rowNumber)
        throws SQLException {
        return new StoredWidgetBootstrapTicket(
            resultSet.getObject("site_id", UUID.class),
            resultSet.getString("origin"),
            resultSet.getString("canonical_page_url"),
            resultSet.getString("page_url_hash"),
            resultSet.getString("public_key_fingerprint"),
            resultSet.getBytes("public_key_spki"),
            resultSet.getObject("expires_at", OffsetDateTime.class).toInstant()
        );
    }

    private StoredWidgetFrameContext mapFrameContext(ResultSet resultSet, int rowNumber)
        throws SQLException {
        return new StoredWidgetFrameContext(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("site_id", UUID.class),
            resultSet.getString("origin"),
            resultSet.getString("page_url_hash"),
            resultSet.getObject("expires_at", OffsetDateTime.class).toInstant()
        );
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
