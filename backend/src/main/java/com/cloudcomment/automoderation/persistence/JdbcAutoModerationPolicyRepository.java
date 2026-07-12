package com.cloudcomment.automoderation.persistence;

import com.cloudcomment.automoderation.domain.ActiveAutoModerationPolicy;
import com.cloudcomment.automoderation.domain.AutoModerationCleanAction;
import com.cloudcomment.automoderation.domain.AutoModerationExecutionMode;
import com.cloudcomment.automoderation.domain.AutoModerationLinkAction;
import com.cloudcomment.automoderation.domain.AutoModerationPolicyConfig;
import com.cloudcomment.automoderation.domain.AutoModerationPolicyLifecycle;
import com.cloudcomment.automoderation.domain.AutoModerationPolicyVersion;
import com.cloudcomment.automoderation.domain.AutoModerationPreset;
import com.cloudcomment.site.domain.AutoModerationStrictness;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class JdbcAutoModerationPolicyRepository implements AutoModerationPolicyRepository {

    private static final String POLICY_COLUMNS = """
        id, site_id, version, revision, lifecycle, enabled, preset, execution_mode,
        review_threshold, spam_threshold, clean_action, link_action, max_links,
        blocked_words, based_on_version_id, created_at, updated_at, published_at
        """;
    private static final String LEGACY_FINGERPRINT = """
        encode(digest(
            s.automod_enabled::text || chr(31)
            || s.automod_strictness || chr(31)
            || s.automod_blocked_words || chr(31)
            || s.automod_hold_links::text || chr(31)
            || s.automod_block_links::text || chr(31)
            || s.automod_max_links::text,
            'sha256'
        ), 'hex')
        """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void initializeFromLegacy(UUID siteId) {
        jdbcTemplate.update(
            """
                insert into automod_policy_versions (
                    site_id, version, lifecycle, enabled, preset, execution_mode,
                    review_threshold, spam_threshold, clean_action, link_action, max_links,
                    blocked_words, created_by, published_at
                )
                select s.id,
                       1,
                       'PUBLISHED',
                       (s.automod_enabled and s.automod_strictness <> 'OFF'),
                       case
                           when btrim(s.automod_blocked_words) <> ''
                               or s.automod_block_links
                               or not s.automod_hold_links
                               or s.automod_max_links <> 2 then 'CUSTOM'
                           when s.automod_strictness = 'RELAXED' then 'OPEN'
                           when s.automod_strictness = 'STRICT' then 'STRICT'
                           else 'BALANCED'
                       end,
                       'LIVE',
                       case s.automod_strictness when 'RELAXED' then 70 when 'STRICT' then 25 else 45 end,
                       case s.automod_strictness when 'RELAXED' then 130 when 'STRICT' then 85 else 90 end,
                       case when s.automod_strictness = 'STRICT' then 'FOLLOW_SITE_MODE' else 'APPROVE' end,
                       case when s.automod_block_links then 'SPAM'
                            when s.automod_hold_links then 'REVIEW' else 'ALLOW' end,
                       case when s.automod_strictness = 'STRICT' and s.automod_hold_links
                            then 0 else s.automod_max_links end,
                       coalesce(
                           (
                               select jsonb_agg(btrim(word) order by ordinal)
                               from unnest(regexp_split_to_array(s.automod_blocked_words, E'\\r?\\n'))
                                    with ordinality as words(word, ordinal)
                               where btrim(word) <> '' and ordinal <= 120
                           ),
                           '[]'::jsonb
                       ),
                       s.owner_id,
                       now()
                from sites s
                where s.id = ?
                on conflict (site_id, version) do nothing
                """,
            siteId
        );
        jdbcTemplate.update(
            """
                insert into site_automod_policy_state (
                    site_id, active_policy_version_id, enabled, execution_mode,
                    last_published_version, legacy_settings_fingerprint
                )
                select p.site_id, p.id, p.enabled, p.execution_mode, p.version,
                       """ + LEGACY_FINGERPRINT + """
                from automod_policy_versions p
                join sites s on s.id = p.site_id
                where p.site_id = ? and p.lifecycle = 'PUBLISHED' and p.version = 1
                on conflict (site_id) do nothing
                """,
            siteId
        );

        Optional<AutoModerationPolicyState> locked = lockState(siteId);
        if (locked.isEmpty()) {
            return;
        }
        AutoModerationPolicyState state = locked.orElseThrow();
        String currentFingerprint = jdbcTemplate.queryForObject(
            "select " + LEGACY_FINGERPRINT + " from sites s where s.id = ?",
            String.class,
            siteId
        );
        if (state.legacySettingsFingerprint().equals(currentFingerprint)) {
            return;
        }

        int version = state.lastPublishedVersion() + 1;
        AutoModerationPolicyVersion reconciled = createPublishedFromLegacy(
            siteId, state.activePolicyVersionId(), version
        );
        jdbcTemplate.update(
            """
                update site_automod_policy_state
                set active_policy_version_id = ?,
                    enabled = ?,
                    execution_mode = ?,
                    last_published_version = ?,
                    legacy_settings_fingerprint = ?,
                    updated_at = now()
                where site_id = ? and active_policy_version_id = ?
                """,
            reconciled.id(),
            reconciled.enabled(),
            reconciled.executionMode().name(),
            version,
            currentFingerprint,
            siteId,
            state.activePolicyVersionId()
        );
    }

    @Override
    public Optional<ActiveAutoModerationPolicy> findActive(UUID siteId) {
        List<ActiveAutoModerationPolicy> rows = jdbcTemplate.query(
            """
                select p.id, p.site_id, p.version, p.revision, p.lifecycle, p.enabled, p.preset,
                       p.execution_mode, p.review_threshold, p.spam_threshold, p.clean_action,
                       p.link_action, p.max_links, p.blocked_words, p.based_on_version_id,
                       p.created_at, p.updated_at, p.published_at,
                       state.enabled as state_enabled, state.execution_mode as state_execution_mode
                from site_automod_policy_state state
                join automod_policy_versions p
                  on p.site_id = state.site_id and p.id = state.active_policy_version_id
                join sites s on s.id = state.site_id
                where state.site_id = ?
                  and p.lifecycle = 'PUBLISHED'
                  and state.legacy_settings_fingerprint = """ + LEGACY_FINGERPRINT,
            (resultSet, rowNumber) -> new ActiveAutoModerationPolicy(
                mapPolicy(resultSet, rowNumber),
                resultSet.getBoolean("state_enabled"),
                AutoModerationExecutionMode.valueOf(resultSet.getString("state_execution_mode"))
            ),
            siteId
        );
        return rows.stream().findFirst();
    }

    @Override
    public Optional<AutoModerationPolicyVersion> findDraft(UUID siteId) {
        return findOne("where site_id = ? and lifecycle = 'DRAFT'", siteId);
    }

    @Override
    public Optional<AutoModerationPolicyVersion> findById(UUID siteId, UUID policyId) {
        return findOne("where site_id = ? and id = ?", siteId, policyId);
    }

    @Override
    public List<AutoModerationPolicyVersion> findPublished(UUID siteId) {
        return jdbcTemplate.query(
            "select " + POLICY_COLUMNS + " from automod_policy_versions "
                + "where site_id = ? and lifecycle = 'PUBLISHED' order by version desc",
            this::mapPolicy,
            siteId
        );
    }

    @Override
    public AutoModerationPolicyVersion createDraft(
        UUID siteId,
        UUID createdBy,
        UUID basedOnVersionId,
        boolean enabled,
        AutoModerationPreset preset,
        AutoModerationExecutionMode executionMode,
        AutoModerationPolicyConfig config
    ) {
        return jdbcTemplate.queryForObject(
            """
                insert into automod_policy_versions (
                    site_id, lifecycle, enabled, preset, execution_mode,
                    review_threshold, spam_threshold, clean_action, link_action, max_links,
                    blocked_words, based_on_version_id, created_by
                )
                values (?, 'DRAFT', ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                returning
                """ + POLICY_COLUMNS,
            this::mapPolicy,
            siteId,
            enabled,
            preset.name(),
            executionMode.name(),
            config.reviewThreshold(),
            config.spamThreshold(),
            config.cleanAction().name(),
            config.linkAction().name(),
            config.maxLinks(),
            serializeBlockedWords(config.blockedWords()),
            basedOnVersionId,
            createdBy
        );
    }

    @Override
    public Optional<AutoModerationPolicyVersion> updateDraft(
        UUID siteId,
        UUID policyId,
        int expectedRevision,
        boolean enabled,
        AutoModerationPreset preset,
        AutoModerationExecutionMode executionMode,
        AutoModerationPolicyConfig config
    ) {
        List<AutoModerationPolicyVersion> rows = jdbcTemplate.query(
            """
                update automod_policy_versions
                set enabled = ?,
                    preset = ?,
                    execution_mode = ?,
                    review_threshold = ?,
                    spam_threshold = ?,
                    clean_action = ?,
                    link_action = ?,
                    max_links = ?,
                    blocked_words = ?::jsonb,
                    revision = revision + 1,
                    updated_at = now()
                where site_id = ? and id = ? and lifecycle = 'DRAFT' and revision = ?
                returning
                """ + POLICY_COLUMNS,
            this::mapPolicy,
            enabled,
            preset.name(),
            executionMode.name(),
            config.reviewThreshold(),
            config.spamThreshold(),
            config.cleanAction().name(),
            config.linkAction().name(),
            config.maxLinks(),
            serializeBlockedWords(config.blockedWords()),
            siteId,
            policyId,
            expectedRevision
        );
        return rows.stream().findFirst();
    }

    @Override
    public boolean deleteDraft(UUID siteId, UUID policyId, int expectedRevision) {
        return jdbcTemplate.update(
            """
                delete from automod_policy_versions
                where site_id = ? and id = ? and lifecycle = 'DRAFT' and revision = ?
                """,
            siteId,
            policyId,
            expectedRevision
        ) > 0;
    }

    @Override
    public Optional<AutoModerationPolicyState> lockState(UUID siteId) {
        List<AutoModerationPolicyState> rows = jdbcTemplate.query(
            """
                select site_id, active_policy_version_id, enabled, execution_mode,
                       last_published_version, legacy_settings_fingerprint
                from site_automod_policy_state
                where site_id = ?
                for update
                """,
            (resultSet, rowNumber) -> new AutoModerationPolicyState(
                resultSet.getObject("site_id", UUID.class),
                resultSet.getObject("active_policy_version_id", UUID.class),
                resultSet.getBoolean("enabled"),
                AutoModerationExecutionMode.valueOf(resultSet.getString("execution_mode")),
                resultSet.getInt("last_published_version"),
                resultSet.getString("legacy_settings_fingerprint")
            ),
            siteId
        );
        return rows.stream().findFirst();
    }

    @Override
    public Optional<AutoModerationPolicyVersion> publishDraft(
        UUID siteId,
        UUID policyId,
        int expectedRevision,
        int version
    ) {
        List<AutoModerationPolicyVersion> rows = jdbcTemplate.query(
            """
                update automod_policy_versions
                set lifecycle = 'PUBLISHED',
                    version = ?,
                    published_at = now(),
                    updated_at = now()
                where site_id = ? and id = ? and lifecycle = 'DRAFT' and revision = ?
                returning
                """ + POLICY_COLUMNS,
            this::mapPolicy,
            version,
            siteId,
            policyId,
            expectedRevision
        );
        return rows.stream().findFirst();
    }

    @Override
    public AutoModerationPolicyVersion copyPublishedVersion(
        UUID siteId,
        UUID sourcePolicyId,
        UUID createdBy,
        int version
    ) {
        return jdbcTemplate.queryForObject(
            """
                insert into automod_policy_versions (
                    site_id, version, lifecycle, enabled, preset, execution_mode,
                    review_threshold, spam_threshold, clean_action, link_action, max_links,
                    blocked_words, based_on_version_id, created_by, published_at
                )
                select site_id, ?, 'PUBLISHED', enabled, preset, execution_mode,
                       review_threshold, spam_threshold, clean_action, link_action, max_links,
                       blocked_words, id, ?, now()
                from automod_policy_versions
                where site_id = ? and id = ? and lifecycle = 'PUBLISHED'
                returning
                """ + POLICY_COLUMNS,
            this::mapPolicy,
            version,
            createdBy,
            siteId,
            sourcePolicyId
        );
    }

    @Override
    public boolean activate(
        UUID siteId,
        UUID expectedActiveVersionId,
        AutoModerationPolicyVersion policy,
        int lastPublishedVersion
    ) {
        return jdbcTemplate.update(
            """
                update site_automod_policy_state
                set active_policy_version_id = ?,
                    enabled = ?,
                    execution_mode = ?,
                    last_published_version = ?,
                    updated_at = now()
                where site_id = ? and active_policy_version_id = ?
                """,
            policy.id(),
            policy.enabled(),
            policy.executionMode().name(),
            lastPublishedVersion,
            siteId,
            expectedActiveVersionId
        ) > 0;
    }

    @Override
    public void synchronizeLegacySettings(UUID siteId, AutoModerationPolicyVersion policy) {
        AutoModerationStrictness strictness = legacyStrictness(policy);
        boolean legacyEnabled = policy.enabled()
            && policy.executionMode() == AutoModerationExecutionMode.LIVE;
        jdbcTemplate.update(
            """
                update sites
                set automod_enabled = ?,
                    automod_strictness = ?,
                    automod_blocked_words = ?,
                    automod_hold_links = ?,
                    automod_block_links = ?,
                    automod_max_links = ?,
                    updated_at = now()
                where id = ?
                """,
            legacyEnabled,
            legacyEnabled ? strictness.name() : AutoModerationStrictness.OFF.name(),
            String.join("\n", policy.config().blockedWords()),
            policy.config().linkAction() == AutoModerationLinkAction.REVIEW,
            policy.config().linkAction() == AutoModerationLinkAction.SPAM,
            policy.config().maxLinks(),
            siteId
        );
        String fingerprintSql = """
                update site_automod_policy_state state
                set legacy_settings_fingerprint = fingerprint.value,
                    updated_at = now()
                from (
                    select s.id,
                """ + LEGACY_FINGERPRINT + """
                           as value
                    from sites s
                    where s.id = ?
                ) fingerprint
                where state.site_id = fingerprint.id
                """;
        jdbcTemplate.update(fingerprintSql, siteId);
    }

    private AutoModerationPolicyVersion createPublishedFromLegacy(
        UUID siteId,
        UUID basedOnVersionId,
        int version
    ) {
        return jdbcTemplate.queryForObject(
            """
                insert into automod_policy_versions (
                    site_id, version, lifecycle, enabled, preset, execution_mode,
                    review_threshold, spam_threshold, clean_action, link_action, max_links,
                    blocked_words, based_on_version_id, created_by, published_at
                )
                select s.id,
                       ?,
                       'PUBLISHED',
                       (s.automod_enabled and s.automod_strictness <> 'OFF'),
                       case
                           when btrim(s.automod_blocked_words) <> ''
                               or s.automod_block_links
                               or not s.automod_hold_links
                               or s.automod_max_links <> 2 then 'CUSTOM'
                           when s.automod_strictness = 'RELAXED' then 'OPEN'
                           when s.automod_strictness = 'STRICT' then 'STRICT'
                           else 'BALANCED'
                       end,
                       'LIVE',
                       case s.automod_strictness when 'RELAXED' then 70 when 'STRICT' then 25 else 45 end,
                       case s.automod_strictness when 'RELAXED' then 130 when 'STRICT' then 85 else 90 end,
                       case when s.automod_strictness = 'STRICT' then 'FOLLOW_SITE_MODE' else 'APPROVE' end,
                       case when s.automod_block_links then 'SPAM'
                            when s.automod_hold_links then 'REVIEW' else 'ALLOW' end,
                       case when s.automod_strictness = 'STRICT' and s.automod_hold_links
                            then 0 else s.automod_max_links end,
                       coalesce(
                           (
                               select jsonb_agg(btrim(word) order by ordinal)
                               from unnest(regexp_split_to_array(s.automod_blocked_words, E'\\r?\\n'))
                                    with ordinality as words(word, ordinal)
                               where btrim(word) <> '' and ordinal <= 120
                           ),
                           '[]'::jsonb
                       ),
                       ?,
                       s.owner_id,
                       now()
                from sites s
                where s.id = ?
                returning
                """ + POLICY_COLUMNS,
            this::mapPolicy,
            version,
            basedOnVersionId,
            siteId
        );
    }

    private Optional<AutoModerationPolicyVersion> findOne(String where, Object... params) {
        List<AutoModerationPolicyVersion> rows = jdbcTemplate.query(
            "select " + POLICY_COLUMNS + " from automod_policy_versions " + where,
            this::mapPolicy,
            params
        );
        return rows.stream().findFirst();
    }

    private AutoModerationPolicyVersion mapPolicy(ResultSet resultSet, int rowNumber) throws SQLException {
        Integer version = resultSet.getObject("version", Integer.class);
        return new AutoModerationPolicyVersion(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("site_id", UUID.class),
            version,
            resultSet.getInt("revision"),
            AutoModerationPolicyLifecycle.valueOf(resultSet.getString("lifecycle")),
            resultSet.getBoolean("enabled"),
            AutoModerationPreset.valueOf(resultSet.getString("preset")),
            AutoModerationExecutionMode.valueOf(resultSet.getString("execution_mode")),
            new AutoModerationPolicyConfig(
                resultSet.getInt("review_threshold"),
                resultSet.getInt("spam_threshold"),
                AutoModerationCleanAction.valueOf(resultSet.getString("clean_action")),
                AutoModerationLinkAction.valueOf(resultSet.getString("link_action")),
                resultSet.getInt("max_links"),
                deserializeBlockedWords(resultSet.getString("blocked_words"))
            ),
            resultSet.getObject("based_on_version_id", UUID.class),
            toInstant(resultSet, "created_at"),
            toInstant(resultSet, "updated_at"),
            toNullableInstant(resultSet, "published_at")
        );
    }

    private String serializeBlockedWords(List<String> blockedWords) {
        return objectMapper.writeValueAsString(blockedWords);
    }

    private List<String> deserializeBlockedWords(String json) throws SQLException {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (RuntimeException exception) {
            throw new SQLException("failed to deserialize automod blocked words", exception);
        }
    }

    private AutoModerationStrictness legacyStrictness(AutoModerationPolicyVersion policy) {
        return switch (policy.preset()) {
            case OPEN -> AutoModerationStrictness.RELAXED;
            case BALANCED -> AutoModerationStrictness.BALANCED;
            case STRICT -> AutoModerationStrictness.STRICT;
            case CUSTOM -> {
                if (policy.config().reviewThreshold() <= 25 && policy.config().spamThreshold() <= 85) {
                    yield AutoModerationStrictness.STRICT;
                }
                if (policy.config().reviewThreshold() >= 70 && policy.config().spamThreshold() >= 130) {
                    yield AutoModerationStrictness.RELAXED;
                }
                yield AutoModerationStrictness.BALANCED;
            }
        };
    }

    private Instant toInstant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    private Instant toNullableInstant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value != null ? value.toInstant() : null;
    }
}
