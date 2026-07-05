package com.cloudcomment.analytics.persistence;

import com.cloudcomment.analytics.domain.ActiveCommenter;
import com.cloudcomment.analytics.domain.AnalyticsBucket;
import com.cloudcomment.analytics.domain.AnalyticsSummary;
import com.cloudcomment.analytics.domain.CommentTimePoint;
import com.cloudcomment.analytics.domain.ModerationStatusCount;
import com.cloudcomment.analytics.domain.ReactionTypeCount;
import com.cloudcomment.analytics.domain.TopPageAnalytics;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
class JdbcOwnerAnalyticsRepository implements OwnerAnalyticsRepository {

    private static final String SITE_SCOPE = " s.owner_id = :ownerId and (cast(:siteId as uuid) is null or s.id = :siteId)";
    private static final String COMMENT_SCOPE = SITE_SCOPE + " and c.deleted_at is null";
    private static final String COMMENT_DATE_SCOPE =
        " and (cast(:from as timestamptz) is null or c.created_at >= :from) and c.created_at <= :to ";
    private static final String REACTION_DATE_SCOPE =
        " and (cast(:from as timestamptz) is null or cr.created_at >= :from) and cr.created_at <= :to ";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public AnalyticsSummary summarize(UUID ownerId, UUID siteId, Instant from, Instant to) {
        return jdbcTemplate.queryForObject(
            """
                select
                    (select count(*)
                     from sites s
                     where """ + SITE_SCOPE + """
                    ) as sites_count,
                    (select count(*)
                     from pages p
                     join sites s on s.id = p.site_id
                     where """ + SITE_SCOPE + """
                    ) as pages_count,
                    (select count(*)
                     from comments c
                     join pages p on p.id = c.page_id
                     join sites s on s.id = p.site_id
                     where """ + COMMENT_SCOPE + COMMENT_DATE_SCOPE + """
                    ) as comments_count,
                    (select count(*)
                     from comments c
                     join pages p on p.id = c.page_id
                     join sites s on s.id = p.site_id
                     where """ + COMMENT_SCOPE + COMMENT_DATE_SCOPE + """
                       and c.parent_id is not null
                    ) as replies_count,
                    (select count(*)
                     from comment_reactions cr
                     join comments c on c.id = cr.comment_id
                     join pages p on p.id = c.page_id
                     join sites s on s.id = p.site_id
                     where """ + COMMENT_SCOPE + REACTION_DATE_SCOPE + """
                    ) as reactions_count,
                    (select count(*)
                     from comments c
                     join pages p on p.id = c.page_id
                     join sites s on s.id = p.site_id
                     where """ + COMMENT_SCOPE + COMMENT_DATE_SCOPE + """
                       and c.status = 'PENDING'
                    ) as pending_count,
                    (select count(*)
                     from comments c
                     join pages p on p.id = c.page_id
                     join sites s on s.id = p.site_id
                     where """ + COMMENT_SCOPE + COMMENT_DATE_SCOPE + """
                       and c.status = 'APPROVED'
                    ) as approved_count,
                    (select count(*)
                     from comments c
                     join pages p on p.id = c.page_id
                     join sites s on s.id = p.site_id
                     where """ + COMMENT_SCOPE + COMMENT_DATE_SCOPE + """
                       and c.status = 'REJECTED'
                    ) as rejected_count,
                    (select count(*)
                     from comments c
                     join pages p on p.id = c.page_id
                     join sites s on s.id = p.site_id
                     where """ + COMMENT_SCOPE + COMMENT_DATE_SCOPE + """
                       and c.status = 'HIDDEN'
                    ) as hidden_count,
                    (select count(*)
                     from comments c
                     join pages p on p.id = c.page_id
                     join sites s on s.id = p.site_id
                     where """ + COMMENT_SCOPE + COMMENT_DATE_SCOPE + """
                       and c.status = 'SPAM'
                    ) as spam_count
                """,
            params(ownerId, siteId, from, to),
            (resultSet, rowNumber) -> new AnalyticsSummary(
                resultSet.getLong("sites_count"),
                resultSet.getLong("pages_count"),
                resultSet.getLong("comments_count"),
                resultSet.getLong("replies_count"),
                resultSet.getLong("reactions_count"),
                resultSet.getLong("pending_count"),
                resultSet.getLong("approved_count"),
                resultSet.getLong("rejected_count"),
                resultSet.getLong("hidden_count"),
                resultSet.getLong("spam_count")
            )
        );
    }

    @Override
    public List<CommentTimePoint> findCommentsOverTime(
        UUID ownerId,
        UUID siteId,
        Instant from,
        Instant to,
        AnalyticsBucket bucket
    ) {
        String bucketExpression = bucket == AnalyticsBucket.MONTH
            ? "date_trunc('month', c.created_at at time zone 'UTC')::date"
            : "date_trunc('day', c.created_at at time zone 'UTC')::date";
        return jdbcTemplate.query(
            """
                select
                    """ + bucketExpression + """
                     as bucket_date,
                    count(*) as total_count,
                    count(*) filter (where c.status = 'APPROVED') as approved_count,
                    count(*) filter (where c.status = 'PENDING') as pending_count,
                    count(*) filter (where c.status = 'SPAM') as spam_count
                from comments c
                join pages p on p.id = c.page_id
                join sites s on s.id = p.site_id
                where """ + COMMENT_SCOPE + COMMENT_DATE_SCOPE + """
                group by bucket_date
                order by bucket_date
                """,
            params(ownerId, siteId, from, to),
            (resultSet, rowNumber) -> new CommentTimePoint(
                resultSet.getObject("bucket_date", LocalDate.class),
                resultSet.getLong("total_count"),
                resultSet.getLong("approved_count"),
                resultSet.getLong("pending_count"),
                resultSet.getLong("spam_count")
            )
        );
    }

    @Override
    public List<ModerationStatusCount> findModerationFunnel(UUID ownerId, UUID siteId, Instant from, Instant to) {
        return jdbcTemplate.query(
            """
                select c.status, count(*) as status_count
                from comments c
                join pages p on p.id = c.page_id
                join sites s on s.id = p.site_id
                where """ + COMMENT_SCOPE + COMMENT_DATE_SCOPE + """
                group by c.status
                order by status_count desc, c.status
                """,
            params(ownerId, siteId, from, to),
            (resultSet, rowNumber) -> new ModerationStatusCount(
                resultSet.getString("status"),
                resultSet.getLong("status_count")
            )
        );
    }

    @Override
    public List<ReactionTypeCount> findReactionDistribution(UUID ownerId, UUID siteId, Instant from, Instant to) {
        return jdbcTemplate.query(
            """
                select cr.reaction_type, count(*) as reaction_count
                from comment_reactions cr
                join comments c on c.id = cr.comment_id
                join pages p on p.id = c.page_id
                join sites s on s.id = p.site_id
                where """ + COMMENT_SCOPE + REACTION_DATE_SCOPE + """
                group by cr.reaction_type
                order by reaction_count desc, cr.reaction_type
                """,
            params(ownerId, siteId, from, to),
            (resultSet, rowNumber) -> new ReactionTypeCount(
                resultSet.getString("reaction_type"),
                resultSet.getLong("reaction_count")
            )
        );
    }

    @Override
    public List<TopPageAnalytics> findTopPages(UUID ownerId, UUID siteId, Instant from, Instant to, int limit) {
        MapSqlParameterSource params = params(ownerId, siteId, from, to).addValue("limit", limit);
        return jdbcTemplate.query(
            """
                select
                    p.id as page_id,
                    s.id as site_id,
                    s.name as site_name,
                    p.url as page_url,
                    count(distinct c.id) as comments_count,
                    count(distinct c.id) filter (where c.parent_id is not null) as replies_count,
                    count(distinct cr.id) as reactions_count,
                    count(distinct c.id) filter (where c.status = 'APPROVED') as approved_count,
                    count(distinct c.id) filter (where c.status = 'PENDING') as pending_count,
                    count(distinct c.id) filter (where c.status = 'SPAM') as spam_count,
                    max(c.created_at) as last_comment_at
                from comments c
                join pages p on p.id = c.page_id
                join sites s on s.id = p.site_id
                left join comment_reactions cr on cr.comment_id = c.id
                    """ + REACTION_DATE_SCOPE + """
                where """ + COMMENT_SCOPE + COMMENT_DATE_SCOPE + """
                group by p.id, s.id, s.name, p.url
                order by comments_count desc, reactions_count desc, last_comment_at desc
                limit :limit
                """,
            params,
            this::mapTopPage
        );
    }

    @Override
    public List<ActiveCommenter> findActiveCommenters(UUID ownerId, UUID siteId, Instant from, Instant to, int limit) {
        MapSqlParameterSource params = params(ownerId, siteId, from, to).addValue("limit", limit);
        return jdbcTemplate.query(
            """
                select
                    c.author_user_id,
                    coalesce(u.email, c.author_email) as author_email,
                    coalesce(u.display_name, c.author_name, c.author_email, u.email) as author_display_name,
                    count(*) as comments_count,
                    count(*) filter (where c.status = 'APPROVED') as approved_count,
                    count(*) filter (where c.status = 'PENDING') as pending_count,
                    count(*) filter (where c.status in ('REJECTED', 'SPAM')) as rejected_or_spam_count,
                    max(c.created_at) as last_activity_at
                from comments c
                join pages p on p.id = c.page_id
                join sites s on s.id = p.site_id
                left join app_users u on u.id = c.author_user_id
                where """ + COMMENT_SCOPE + COMMENT_DATE_SCOPE + """
                group by c.author_user_id, coalesce(u.email, c.author_email), coalesce(u.display_name, c.author_name, c.author_email, u.email)
                order by comments_count desc, last_activity_at desc
                limit :limit
                """,
            params,
            this::mapActiveCommenter
        );
    }

    private TopPageAnalytics mapTopPage(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TopPageAnalytics(
            resultSet.getObject("page_id", UUID.class),
            resultSet.getObject("site_id", UUID.class),
            resultSet.getString("site_name"),
            resultSet.getString("page_url"),
            resultSet.getLong("comments_count"),
            resultSet.getLong("replies_count"),
            resultSet.getLong("reactions_count"),
            resultSet.getLong("approved_count"),
            resultSet.getLong("pending_count"),
            resultSet.getLong("spam_count"),
            toInstant(resultSet, "last_comment_at")
        );
    }

    private ActiveCommenter mapActiveCommenter(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ActiveCommenter(
            resultSet.getObject("author_user_id", UUID.class),
            resultSet.getString("author_email"),
            resultSet.getString("author_display_name"),
            resultSet.getLong("comments_count"),
            resultSet.getLong("approved_count"),
            resultSet.getLong("pending_count"),
            resultSet.getLong("rejected_or_spam_count"),
            toInstant(resultSet, "last_activity_at")
        );
    }

    private MapSqlParameterSource params(UUID ownerId, UUID siteId, Instant from, Instant to) {
        return new MapSqlParameterSource()
            .addValue("ownerId", ownerId)
            .addValue("siteId", siteId)
            .addValue("from", toOffsetDateTime(from))
            .addValue("to", toOffsetDateTime(to));
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private Instant toInstant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value != null ? value.toInstant() : null;
    }
}
