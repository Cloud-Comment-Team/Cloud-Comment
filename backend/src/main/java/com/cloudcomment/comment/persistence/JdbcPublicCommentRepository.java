package com.cloudcomment.comment.persistence;

import com.cloudcomment.automoderation.domain.AutoModerationSnapshot;
import com.cloudcomment.comment.application.CommentPage;
import com.cloudcomment.comment.application.WidgetSite;
import com.cloudcomment.comment.domain.CommentAuthor;
import com.cloudcomment.comment.domain.CommentReactionSummary;
import com.cloudcomment.comment.domain.CommentReactionType;
import com.cloudcomment.comment.domain.CommentStatus;
import com.cloudcomment.comment.domain.CommentView;
import com.cloudcomment.comment.domain.PublicCommentSort;
import com.cloudcomment.site.domain.AutoModerationSettings;
import com.cloudcomment.site.domain.AutoModerationStrictness;
import com.cloudcomment.site.domain.ModerationMode;
import com.cloudcomment.site.domain.WidgetCornerRadius;
import com.cloudcomment.site.domain.WidgetStyle;
import com.cloudcomment.site.domain.WidgetTheme;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
class JdbcPublicCommentRepository implements PublicCommentRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<WidgetSite> findActiveSite(UUID siteId) {
        List<WidgetSite> sites = jdbcTemplate.query(
            """
                select id,
                       moderation_mode,
                       widget_theme,
                       widget_accent_color,
                       widget_corner_radius,
                       widget_style_config,
                       automod_enabled,
                       automod_strictness,
                       automod_blocked_words,
                       automod_hold_links,
                       automod_block_links,
                       automod_max_links
                from sites
                where id = ? and is_active = true
                """,
            (resultSet, rowNumber) -> new WidgetSite(
                resultSet.getObject("id", UUID.class),
                ModerationMode.valueOf(resultSet.getString("moderation_mode")),
                deserializeWidgetStyle(resultSet),
                new AutoModerationSettings(
                    resultSet.getBoolean("automod_enabled"),
                    AutoModerationStrictness.valueOf(resultSet.getString("automod_strictness")),
                    parseBlockedWords(resultSet.getString("automod_blocked_words")),
                    resultSet.getBoolean("automod_hold_links"),
                    resultSet.getBoolean("automod_block_links"),
                    resultSet.getInt("automod_max_links")
                )
            ),
            siteId
        );
        return sites.stream().findFirst();
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

    @Override
    public boolean isAllowedOrigin(UUID siteId, String normalizedOrigin) {
        Boolean exists = jdbcTemplate.queryForObject(
            """
                select exists(
                    select 1
                    from site_allowed_origins
                    where site_id = ? and origin = ?
                )
                """,
            Boolean.class,
            siteId,
            normalizedOrigin
        );
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public Optional<UUID> findPageId(UUID siteId, String pageUrl) {
        List<UUID> pageIds = jdbcTemplate.queryForList(
            """
                select id
                from pages
                where site_id = ? and url = ?
                """,
            UUID.class,
            siteId,
            pageUrl
        );
        return pageIds.stream().findFirst();
    }

    @Override
    @Transactional
    public UUID findOrCreatePage(UUID siteId, String pageUrl) {
        UUID pageId = jdbcTemplate.queryForObject(
            """
                with inserted as (
                    insert into pages (site_id, url)
                    values (?, ?)
                    on conflict (site_id, url) do nothing
                    returning id
                )
                select id from inserted
                union all
                select id from pages where site_id = ? and url = ?
                limit 1
                """,
            UUID.class,
            siteId,
            pageUrl,
            siteId,
            pageUrl
        );
        return Objects.requireNonNull(pageId, "page id must not be null");
    }

    @Override
    public CommentPage findApprovedComments(UUID siteId, UUID pageId, int page, int pageSize) {
        return findApprovedComments(siteId, pageId, page, pageSize, PublicCommentSort.PINNED_FIRST, Optional.empty());
    }

    @Override
    public CommentPage findApprovedComments(
        UUID siteId,
        UUID pageId,
        int page,
        int pageSize,
        PublicCommentSort sort,
        Optional<UUID> viewerUserId
    ) {
        return findApprovedComments(siteId, pageId, page, pageSize, sort, viewerUserId, null);
    }

    @Override
    public CommentPage findApprovedComments(
        UUID siteId,
        UUID pageId,
        int page,
        int pageSize,
        PublicCommentSort sort,
        Optional<UUID> viewerUserId,
        Integer replyLimit
    ) {
        long offset = ((long) page - 1) * pageSize;
        List<CommentRow> rootRows = jdbcTemplate.query(
            """
                select c.id,
                       p.site_id,
                       c.page_id,
                       c.parent_id,
                       c.author_user_id,
                       coalesce(c.author_email, u.email) as author_email,
                       coalesce(c.author_name, nullif(u.display_name, ''), c.author_email, u.email) as author_name,
                       c.body,
                       c.status,
                       c.is_pinned,
                       c.created_at,
                       c.updated_at,
                       c.edited_at
                from comments c
                join pages p on p.id = c.page_id
                left join app_users u on u.id = c.author_user_id
                left join (
                    select coalesce(visible_comments.parent_id, visible_comments.id) as root_id,
                           count(*) as reaction_count
                    from comments visible_comments
                    join comment_reactions cr on cr.comment_id = visible_comments.id
                    where visible_comments.page_id = ?
                      and visible_comments.status = 'APPROVED'
                      and visible_comments.deleted_at is null
                    group by coalesce(visible_comments.parent_id, visible_comments.id)
                ) thread_reactions on thread_reactions.root_id = c.id
                where p.site_id = ?
                  and c.page_id = ?
                  and c.status = 'APPROVED'
                  and c.parent_id is null
                  and c.deleted_at is null
                """ + publicOrderBy(sort) + """
                limit ? offset ?
                """,
            this::mapCommentRow,
            pageId,
            siteId,
            pageId,
            pageSize,
            offset
        );

        Long totalItems = jdbcTemplate.queryForObject(
            """
                select count(*)
                from comments
                where page_id = ?
                  and status = 'APPROVED'
                  and parent_id is null
                  and deleted_at is null
                """,
            Long.class,
            pageId
        );

        List<CommentRow> replyRows = findApprovedReplyRows(rootRows, replyLimit);
        Map<UUID, Long> replyCounts = findReplyCounts(rootRows);
        Map<UUID, List<CommentReactionSummary>> reactionsByComment = findReactionSummaries(
            visibleCommentIds(rootRows, replyRows),
            viewerUserId
        );

        return new CommentPage(
            buildThreads(rootRows, replyRows, replyCounts, reactionsByComment, viewerUserId),
            page,
            pageSize,
            totalItemsOrZero(totalItems)
        );
    }

    @Override
    public CommentPage findApprovedReplies(
        UUID siteId,
        UUID rootCommentId,
        int page,
        int pageSize,
        Optional<UUID> viewerUserId
    ) {
        long offset = ((long) page - 1) * pageSize;
        List<CommentRow> rows = jdbcTemplate.query(
            """
                select c.id, p.site_id, c.page_id, c.parent_id, c.author_user_id,
                       coalesce(c.author_email, u.email) as author_email,
                       coalesce(c.author_name, nullif(u.display_name, ''), c.author_email, u.email) as author_name,
                       c.body, c.status, c.is_pinned, c.created_at, c.updated_at, c.edited_at
                from comments c
                join pages p on p.id = c.page_id
                join comments root on root.id = c.parent_id and root.page_id = c.page_id
                left join app_users u on u.id = c.author_user_id
                where p.site_id = ?
                  and root.id = ?
                  and root.parent_id is null
                  and root.status = 'APPROVED'
                  and root.deleted_at is null
                  and c.status = 'APPROVED'
                  and c.deleted_at is null
                order by c.created_at asc, c.id asc
                limit ? offset ?
                """,
            this::mapCommentRow,
            siteId, rootCommentId, pageSize, offset
        );
        Long totalItems = jdbcTemplate.queryForObject(
            """
                select count(*)
                from comments c
                join pages p on p.id = c.page_id
                join comments root on root.id = c.parent_id and root.page_id = c.page_id
                where p.site_id = ? and root.id = ? and root.parent_id is null
                  and root.status = 'APPROVED' and root.deleted_at is null
                  and c.status = 'APPROVED' and c.deleted_at is null
                """,
            Long.class,
            siteId,
            rootCommentId
        );
        Map<UUID, List<CommentReactionSummary>> reactions = findReactionSummaries(
            rows.stream().map(CommentRow::id).toList(), viewerUserId
        );
        return new CommentPage(
            rows.stream().map(row -> toCommentView(
                row, reactions.getOrDefault(row.id(), List.of()), List.of(), 0, viewerUserId
            )).toList(),
            page,
            pageSize,
            totalItemsOrZero(totalItems)
        );
    }

    @Override
    public boolean existsApprovedRootCommentOnPage(UUID pageId, UUID commentId) {
        Boolean exists = jdbcTemplate.queryForObject(
            """
                select exists(
                    select 1
                    from comments
                    where id = ?
                      and page_id = ?
                      and status = 'APPROVED'
                      and parent_id is null
                      and deleted_at is null
                )
                """,
            Boolean.class,
            commentId,
            pageId
        );
        return Boolean.TRUE.equals(exists);
    }

    @Override
    @Transactional
    public CommentView createComment(
        UUID siteId,
        UUID pageId,
        UUID parentId,
        UUID authorUserId,
        String authorName,
        String authorEmail,
        String content,
        CommentStatus status
    ) {
        return createComment(siteId, pageId, parentId, authorUserId, authorName, authorEmail, content, status, null);
    }

    @Override
    @Transactional
    public CommentView createComment(
        UUID siteId,
        UUID pageId,
        UUID parentId,
        UUID authorUserId,
        String authorName,
        String authorEmail,
        String content,
        CommentStatus status,
        String moderationReason
    ) {
        return createComment(
            siteId,
            pageId,
            parentId,
            authorUserId,
            authorName,
            authorEmail,
            content,
            status,
            moderationReason,
            null
        );
    }

    @Override
    @Transactional
    public CommentView createComment(
        UUID siteId,
        UUID pageId,
        UUID parentId,
        UUID authorUserId,
        String authorName,
        String authorEmail,
        String content,
        CommentStatus status,
        String moderationReason,
        AutoModerationSnapshot autoModeration
    ) {
        CommentRow row = jdbcTemplate.queryForObject(
            """
                insert into comments (
                    page_id,
                    parent_id,
                    author_user_id,
                    author_name,
                    author_email,
                    body,
                    status,
                    moderation_reason,
                    automod_policy_version_id,
                    automod_execution_mode,
                    automod_score,
                    automod_decision,
                    automod_signals,
                    automod_reason,
                    automod_applied_status,
                    automod_evaluated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                returning id,
                          ?::uuid as site_id,
                          page_id,
                          parent_id,
                          author_user_id,
                          author_email,
                          author_name,
                          body,
                          status,
                          is_pinned,
                          created_at,
                          updated_at,
                          edited_at
                """,
            this::mapCommentRow,
            pageId,
            parentId,
            authorUserId,
            authorName,
            authorEmail,
            content,
            status.name(),
            moderationReason,
            autoModeration != null ? autoModeration.policyVersionId() : null,
            autoModeration != null ? autoModeration.executionMode().name() : null,
            autoModeration != null ? autoModeration.score() : null,
            autoModeration != null ? autoModeration.decision().name() : null,
            autoModeration != null ? serializeAutoModerationSignals(autoModeration) : null,
            autoModeration != null ? autoModeration.reason() : null,
            autoModeration != null ? autoModeration.appliedStatus().name() : null,
            autoModeration != null ? autoModeration.evaluatedAt().atOffset(ZoneOffset.UTC) : null,
            siteId
        );
        return toCommentView(
            Objects.requireNonNull(row, "created comment must not be null"),
            List.of(),
            Optional.of(authorUserId)
        );
    }

    @Override
    public boolean existsApprovedCommentInSite(UUID siteId, UUID commentId) {
        Boolean exists = jdbcTemplate.queryForObject(
            """
                select exists(
                    select 1
                    from comments c
                    join pages p on p.id = c.page_id
                    where c.id = ?
                      and p.site_id = ?
                      and c.status = 'APPROVED'
                      and c.deleted_at is null
                )
                """,
            Boolean.class,
            commentId,
            siteId
        );
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public Optional<CommentStatus> findOwnCommentStatus(UUID siteId, UUID commentId, UUID authorUserId) {
        List<CommentStatus> rows = jdbcTemplate.query(
            """
                select c.status
                from comments c
                join pages p on p.id = c.page_id
                where p.site_id = ?
                  and c.id = ?
                  and c.author_user_id = ?
                  and c.deleted_at is null
                for update
                """,
            (resultSet, rowNumber) -> CommentStatus.valueOf(resultSet.getString("status")),
            siteId,
            commentId,
            authorUserId
        );
        return rows.stream().findFirst();
    }

    @Override
    @Transactional
    public Optional<CommentView> updateOwnComment(
        UUID siteId,
        UUID commentId,
        UUID authorUserId,
        String content,
        CommentStatus status,
        String moderationReason
    ) {
        return updateOwnComment(siteId, commentId, authorUserId, content, status, moderationReason, null);
    }

    @Override
    @Transactional
    public Optional<CommentView> updateOwnComment(
        UUID siteId,
        UUID commentId,
        UUID authorUserId,
        String content,
        CommentStatus status,
        String moderationReason,
        AutoModerationSnapshot autoModeration
    ) {
        List<CommentRow> rows = jdbcTemplate.query(
            """
                update comments c
                set body = ?,
                    status = ?,
                    moderation_reason = ?,
                    automod_policy_version_id = ?,
                    automod_execution_mode = ?,
                    automod_score = ?,
                    automod_decision = ?,
                    automod_signals = ?::jsonb,
                    automod_reason = ?,
                    automod_applied_status = ?,
                    automod_evaluated_at = ?,
                    edited_at = now(),
                    updated_at = now()
                from pages p
                where p.id = c.page_id
                  and p.site_id = ?
                  and c.id = ?
                  and c.author_user_id = ?
                  and c.deleted_at is null
                returning c.id,
                          p.site_id,
                          c.page_id,
                          c.parent_id,
                          c.author_user_id,
                          c.author_email,
                          c.author_name,
                          c.body,
                          c.status,
                          c.is_pinned,
                          c.created_at,
                          c.updated_at,
                          c.edited_at
                """,
            this::mapCommentRow,
            content,
            status.name(),
            moderationReason,
            autoModeration != null ? autoModeration.policyVersionId() : null,
            autoModeration != null ? autoModeration.executionMode().name() : null,
            autoModeration != null ? autoModeration.score() : null,
            autoModeration != null ? autoModeration.decision().name() : null,
            autoModeration != null ? serializeAutoModerationSignals(autoModeration) : null,
            autoModeration != null ? autoModeration.reason() : null,
            autoModeration != null ? autoModeration.appliedStatus().name() : null,
            autoModeration != null ? autoModeration.evaluatedAt().atOffset(ZoneOffset.UTC) : null,
            siteId,
            commentId,
            authorUserId
        );
        return rows.stream()
            .findFirst()
            .map(row -> toCommentView(row, List.of(), Optional.of(authorUserId)));
    }

    private String serializeAutoModerationSignals(AutoModerationSnapshot snapshot) {
        return objectMapper.writeValueAsString(snapshot.signals());
    }

    @Override
    @Transactional
    public boolean softDeleteOwnComment(UUID siteId, UUID commentId, UUID authorUserId) {
        int updatedRows = jdbcTemplate.update(
            """
                update comments c
                set body = '[deleted by author]',
                    status = 'HIDDEN',
                    moderation_reason = 'Deleted by author',
                    deleted_at = now(),
                    deleted_by_author = true,
                    updated_at = now()
                from pages p
                where p.id = c.page_id
                  and p.site_id = ?
                  and c.id = ?
                  and c.author_user_id = ?
                  and c.deleted_at is null
                """,
            siteId,
            commentId,
            authorUserId
        );
        if (updatedRows > 0) {
            jdbcTemplate.update("delete from comment_reactions where comment_id = ?", commentId);
        }
        return updatedRows > 0;
    }

    @Override
    @Transactional
    public List<CommentReactionSummary> setReaction(
        UUID commentId,
        UUID userId,
        CommentReactionType reactionType
    ) {
        jdbcTemplate.update(
            """
                insert into comment_reactions (comment_id, user_id, reaction_type)
                values (?, ?, ?)
                on conflict (comment_id, user_id)
                do update set reaction_type = excluded.reaction_type,
                              updated_at = now()
                """,
            commentId,
            userId,
            reactionType.name()
        );
        return findReactionSummaries(List.of(commentId), Optional.of(userId))
            .getOrDefault(commentId, emptyReactionSummaries(Optional.of(userId), Map.of()));
    }

    @Override
    @Transactional
    public List<CommentReactionSummary> clearReaction(UUID commentId, UUID userId) {
        jdbcTemplate.update(
            """
                delete from comment_reactions
                where comment_id = ? and user_id = ?
                """,
            commentId,
            userId
        );
        return findReactionSummaries(List.of(commentId), Optional.of(userId))
            .getOrDefault(commentId, emptyReactionSummaries(Optional.of(userId), Map.of()));
    }

    private List<CommentRow> findApprovedReplyRows(List<CommentRow> rootRows, Integer replyLimit) {
        List<UUID> rootIds = rootRows.stream().map(CommentRow::id).toList();
        if (rootIds.isEmpty()) {
            return List.of();
        }

        return namedParameterJdbcTemplate.query(
            """
                select id, site_id, page_id, parent_id, author_user_id, author_email, author_name,
                       body, status, is_pinned, created_at, updated_at, edited_at
                from (
                    select c.id, p.site_id, c.page_id, c.parent_id, c.author_user_id,
                           coalesce(c.author_email, u.email) as author_email,
                           coalesce(c.author_name, nullif(u.display_name, ''), c.author_email, u.email) as author_name,
                           c.body, c.status, c.is_pinned, c.created_at, c.updated_at, c.edited_at,
                           row_number() over (partition by c.parent_id order by c.created_at asc, c.id asc) as reply_rank
                    from comments c
                    join pages p on p.id = c.page_id
                    left join app_users u on u.id = c.author_user_id
                    where c.parent_id in (:rootIds)
                      and c.status = 'APPROVED'
                      and c.deleted_at is null
                ) ranked_replies
                where :replyLimit is null or reply_rank <= :replyLimit
                order by parent_id, created_at asc, id asc
                """,
            new MapSqlParameterSource("rootIds", rootIds)
                .addValue("replyLimit", replyLimit, Types.INTEGER),
            this::mapCommentRow
        );
    }

    private Map<UUID, Long> findReplyCounts(List<CommentRow> rootRows) {
        List<UUID> rootIds = rootRows.stream().map(CommentRow::id).toList();
        if (rootIds.isEmpty()) {
            return Map.of();
        }
        List<Map.Entry<UUID, Long>> rows = namedParameterJdbcTemplate.query(
            """
                select parent_id, count(*) as reply_count
                from comments
                where parent_id in (:rootIds) and status = 'APPROVED' and deleted_at is null
                group by parent_id
                """,
            new MapSqlParameterSource("rootIds", rootIds),
            (resultSet, rowNumber) -> Map.entry(
                resultSet.getObject("parent_id", UUID.class), resultSet.getLong("reply_count")
            )
        );
        Map<UUID, Long> counts = new HashMap<>();
        rows.forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        return counts;
    }

    private List<CommentView> buildThreads(
        List<CommentRow> rootRows,
        List<CommentRow> replyRows,
        Map<UUID, Long> replyCounts,
        Map<UUID, List<CommentReactionSummary>> reactionsByComment,
        Optional<UUID> viewerUserId
    ) {
        Map<UUID, MutableComment> comments = new LinkedHashMap<>();
        for (CommentRow row : rootRows) {
            comments.put(row.id(), new MutableComment(row, reactionsByComment, viewerUserId));
        }
        for (CommentRow row : replyRows) {
            MutableComment comment = new MutableComment(row, reactionsByComment, viewerUserId);
            comments.put(row.id(), comment);
            MutableComment parent = comments.get(row.parentId());
            if (parent != null) {
                parent.replies().add(comment);
            }
        }
        return rootRows.stream()
            .map(row -> comments.get(row.id()).toView(replyCounts.getOrDefault(row.id(), 0L)))
            .toList();
    }

    private CommentView toCommentView(
        CommentRow row,
        List<CommentReactionSummary> reactions,
        List<CommentView> replies,
        long replyCount,
        Optional<UUID> viewerUserId
    ) {
        return new CommentView(
            row.id(),
            row.siteId(),
            row.pageId(),
            row.parentId(),
            new CommentAuthor(row.authorUserId(), row.authorEmail(), row.authorName()),
            row.body(),
            row.status(),
            row.createdAt(),
            row.updatedAt(),
            row.editedAt(),
            row.pinned(),
            viewerUserId.filter(userId -> userId.equals(row.authorUserId())).isPresent(),
            reactions,
            replyCount,
            replies
        );
    }

    private CommentView toCommentView(CommentRow row, List<CommentView> replies, Optional<UUID> viewerUserId) {
        return toCommentView(row, List.of(), replies, replies.size(), viewerUserId);
    }

    private List<UUID> visibleCommentIds(List<CommentRow> rootRows, List<CommentRow> replyRows) {
        List<UUID> ids = new ArrayList<>(rootRows.size() + replyRows.size());
        rootRows.stream().map(CommentRow::id).forEach(ids::add);
        replyRows.stream().map(CommentRow::id).forEach(ids::add);
        return ids;
    }

    private Map<UUID, List<CommentReactionSummary>> findReactionSummaries(
        List<UUID> commentIds,
        Optional<UUID> viewerUserId
    ) {
        if (commentIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Map<CommentReactionType, ReactionAggregate>> aggregates = new HashMap<>();
        namedParameterJdbcTemplate.query(
            """
                select comment_id,
                       reaction_type,
                       count(*) as reaction_count,
                       bool_or(user_id = :viewerUserId) as reacted_by_current_user
                from comment_reactions
                where comment_id in (:commentIds)
                group by comment_id, reaction_type
                """,
            new MapSqlParameterSource()
                .addValue("commentIds", commentIds)
                .addValue("viewerUserId", viewerUserId.orElse(null), Types.OTHER),
            resultSet -> {
                UUID commentId = resultSet.getObject("comment_id", UUID.class);
                CommentReactionType type = CommentReactionType.valueOf(resultSet.getString("reaction_type"));
                ReactionAggregate aggregate = new ReactionAggregate(
                    resultSet.getLong("reaction_count"),
                    resultSet.getBoolean("reacted_by_current_user")
                );
                aggregates
                    .computeIfAbsent(commentId, ignored -> new EnumMap<>(CommentReactionType.class))
                    .put(type, aggregate);
            }
        );

        Map<UUID, List<CommentReactionSummary>> summaries = new HashMap<>();
        for (UUID commentId : commentIds) {
            summaries.put(commentId, emptyReactionSummaries(viewerUserId, aggregates.getOrDefault(
                commentId,
                Map.of()
            )));
        }
        return summaries;
    }

    private List<CommentReactionSummary> emptyReactionSummaries(
        Optional<UUID> viewerUserId,
        Map<CommentReactionType, ReactionAggregate> aggregates
    ) {
        List<CommentReactionSummary> summaries = new ArrayList<>(CommentReactionType.values().length);
        for (CommentReactionType type : CommentReactionType.values()) {
            ReactionAggregate aggregate = aggregates.getOrDefault(type, ReactionAggregate.EMPTY);
            summaries.add(new CommentReactionSummary(
                type,
                aggregate.count(),
                viewerUserId.isPresent() && aggregate.reactedByCurrentUser()
            ));
        }
        return summaries;
    }

    private CommentRow mapCommentRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CommentRow(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("site_id", UUID.class),
            resultSet.getObject("page_id", UUID.class),
            resultSet.getObject("parent_id", UUID.class),
            resultSet.getObject("author_user_id", UUID.class),
            resultSet.getString("author_email"),
            resultSet.getString("author_name"),
            resultSet.getString("body"),
            CommentStatus.valueOf(resultSet.getString("status")),
            resultSet.getBoolean("is_pinned"),
            toInstant(resultSet, "created_at"),
            toInstant(resultSet, "updated_at"),
            toNullableInstant(resultSet, "edited_at")
        );
    }

    private String publicOrderBy(PublicCommentSort sort) {
        PublicCommentSort effectiveSort = sort != null ? sort : PublicCommentSort.PINNED_FIRST;
        return switch (effectiveSort) {
            case PINNED_FIRST, OLDEST -> " order by c.is_pinned desc, c.created_at asc, c.id asc\n";
            case NEWEST -> " order by c.is_pinned desc, c.created_at desc, c.id desc\n";
            case TOP_REACTIONS -> """
                 order by c.is_pinned desc,
                          coalesce(thread_reactions.reaction_count, 0) desc,
                          c.created_at asc,
                          c.id asc
                """;
        };
    }

    private Instant toInstant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    private Instant toNullableInstant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value != null ? value.toInstant() : null;
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

    private long totalItemsOrZero(Long totalItems) {
        return Objects.requireNonNullElse(totalItems, 0L);
    }

    private record CommentRow(
        UUID id,
        UUID siteId,
        UUID pageId,
        UUID parentId,
        UUID authorUserId,
        String authorEmail,
        String authorName,
        String body,
        CommentStatus status,
        boolean pinned,
        Instant createdAt,
        Instant updatedAt,
        Instant editedAt
    ) {
    }

    private record ReactionAggregate(
        long count,
        boolean reactedByCurrentUser
    ) {

        private static final ReactionAggregate EMPTY = new ReactionAggregate(0, false);
    }

    private final class MutableComment {

        private final CommentRow row;
        private final Map<UUID, List<CommentReactionSummary>> reactionsByComment;
        private final Optional<UUID> viewerUserId;
        private final List<MutableComment> replies = new ArrayList<>();

        private MutableComment(
            CommentRow row,
            Map<UUID, List<CommentReactionSummary>> reactionsByComment,
            Optional<UUID> viewerUserId
        ) {
            this.row = row;
            this.reactionsByComment = reactionsByComment;
            this.viewerUserId = viewerUserId;
        }

        private List<MutableComment> replies() {
            return replies;
        }

        private CommentView toView(long replyCount) {
            return toCommentView(
                row,
                reactionsByComment.getOrDefault(row.id(), List.of()),
                replies.stream().map(reply -> reply.toView(0)).toList(),
                replyCount,
                viewerUserId
            );
        }
    }
}
