package com.cloudcomment.automoderation.application;

import com.cloudcomment.access.application.ResourceOwnershipService;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.automoderation.domain.ActiveAutoModerationPolicy;
import com.cloudcomment.automoderation.domain.AutoModerationCleanAction;
import com.cloudcomment.automoderation.domain.AutoModerationDecisionType;
import com.cloudcomment.automoderation.domain.AutoModerationExecutionMode;
import com.cloudcomment.automoderation.domain.AutoModerationLinkAction;
import com.cloudcomment.automoderation.domain.AutoModerationPolicyConfig;
import com.cloudcomment.automoderation.domain.AutoModerationPolicyLifecycle;
import com.cloudcomment.automoderation.domain.AutoModerationPolicyVersion;
import com.cloudcomment.automoderation.domain.AutoModerationPreset;
import com.cloudcomment.automoderation.persistence.AutoModerationPolicyRepository;
import com.cloudcomment.automoderation.persistence.AutoModerationPolicyState;
import com.cloudcomment.comment.application.AutoModerationDecision;
import com.cloudcomment.comment.application.AutoModerationService;
import com.cloudcomment.comment.application.AutoModerationSignal;
import com.cloudcomment.comment.domain.CommentStatus;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.site.domain.AutoModerationSettings;
import com.cloudcomment.site.domain.AutoModerationStrictness;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.Clock;

@Service
@RequiredArgsConstructor
public class AutoModerationPolicyService {

    private static final int MAX_REASON_LENGTH = 500;

    private final AutoModerationPolicyRepository repository;
    private final ResourceOwnershipService ownershipService;
    private final AutoModerationService evaluator;
    private final Clock clock;

    @Transactional
    public void initializeSite(UUID siteId) {
        repository.initializeFromLegacy(siteId);
    }

    @Transactional
    public AutoModerationPolicySet list(AuthenticatedUser currentUser, UUID siteId) {
        ownershipService.assertSiteOwnedBy(currentUser, siteId);
        ActiveAutoModerationPolicy active = requireActiveOrInitialize(siteId);
        return new AutoModerationPolicySet(
            activeVersion(active),
            repository.findDraft(siteId).orElse(null),
            repository.findPublished(siteId)
        );
    }

    @Transactional
    public AutoModerationPolicyVersion createDraft(
        AuthenticatedUser currentUser,
        UUID siteId,
        AutoModerationPreset requestedPreset,
        Boolean requestedEnabled,
        AutoModerationExecutionMode requestedMode
    ) {
        ownershipService.assertSiteOwnedBy(currentUser, siteId);
        requireActiveOrInitialize(siteId);
        AutoModerationPolicyState state = lockState(siteId);
        if (repository.findDraft(siteId).isPresent()) {
            throw conflict("A draft already exists");
        }
        ActiveAutoModerationPolicy active = requireActive(siteId);
        if (!active.version().id().equals(state.activePolicyVersionId())) {
            throw conflict("Active policy changed in another session");
        }
        AutoModerationPreset preset = requestedPreset != null ? requestedPreset : active.version().preset();
        AutoModerationPolicyConfig config = requestedPreset == null || requestedPreset == active.version().preset()
            ? active.version().config()
            : AutoModerationPolicyConfig.preset(preset);
        try {
            return repository.createDraft(
                siteId,
                currentUser.id(),
                active.version().id(),
                requestedEnabled != null ? requestedEnabled : active.enabled(),
                preset,
                requestedMode != null ? requestedMode : active.executionMode(),
                config
            );
        } catch (DuplicateKeyException exception) {
            throw conflict("A draft already exists");
        }
    }

    @Transactional
    public AutoModerationPolicyVersion updateDraft(
        AuthenticatedUser currentUser,
        UUID siteId,
        UUID policyId,
        int expectedRevision,
        Boolean enabled,
        AutoModerationPreset preset,
        AutoModerationExecutionMode executionMode,
        Integer reviewThreshold,
        Integer spamThreshold,
        AutoModerationCleanAction cleanAction,
        AutoModerationLinkAction linkAction,
        Integer maxLinks,
        List<String> blockedWords
    ) {
        ownershipService.assertSiteOwnedBy(currentUser, siteId);
        AutoModerationPolicyVersion current = requireDraft(siteId, policyId);
        AutoModerationPreset nextPreset = preset != null ? preset : current.preset();
        AutoModerationPolicyConfig config;
        if (nextPreset != AutoModerationPreset.CUSTOM) {
            config = AutoModerationPolicyConfig.preset(nextPreset);
        } else {
            AutoModerationPolicyConfig source = current.preset() == AutoModerationPreset.CUSTOM
                ? current.config()
                : AutoModerationPolicyConfig.preset(current.preset());
            config = new AutoModerationPolicyConfig(
                reviewThreshold != null ? reviewThreshold : source.reviewThreshold(),
                spamThreshold != null ? spamThreshold : source.spamThreshold(),
                cleanAction != null ? cleanAction : source.cleanAction(),
                linkAction != null ? linkAction : source.linkAction(),
                maxLinks != null ? maxLinks : source.maxLinks(),
                blockedWords != null ? blockedWords : source.blockedWords()
            );
        }
        AutoModerationPolicyConfig normalized = normalizeConfig(config);
        return repository.updateDraft(
            siteId,
            policyId,
            expectedRevision,
            enabled != null ? enabled : current.enabled(),
            nextPreset,
            executionMode != null ? executionMode : current.executionMode(),
            normalized
        ).orElseThrow(() -> conflict("Draft was changed in another session"));
    }

    @Transactional
    public void deleteDraft(
        AuthenticatedUser currentUser,
        UUID siteId,
        UUID policyId,
        int expectedRevision
    ) {
        ownershipService.assertSiteOwnedBy(currentUser, siteId);
        requireDraft(siteId, policyId);
        if (!repository.deleteDraft(siteId, policyId, expectedRevision)) {
            throw conflict("Draft was changed in another session");
        }
    }

    @Transactional(readOnly = true)
    public AutoModerationEvaluation simulate(
        AuthenticatedUser currentUser,
        UUID siteId,
        UUID policyId,
        String content,
        CommentStatus baselineStatus
    ) {
        ownershipService.assertSiteOwnedBy(currentUser, siteId);
        AutoModerationPolicyVersion policy = repository.findById(siteId, policyId).orElseThrow(this::notFound);
        return evaluate(content, policy, baselineStatus, baselineStatus);
    }

    @Transactional
    public AutoModerationPolicyVersion publish(
        AuthenticatedUser currentUser,
        UUID siteId,
        UUID policyId,
        int expectedRevision,
        UUID expectedActiveVersionId
    ) {
        ownershipService.assertSiteOwnedBy(currentUser, siteId);
        requireActiveOrInitialize(siteId);
        AutoModerationPolicyState state = lockState(siteId);
        assertExpectedActive(state, expectedActiveVersionId);
        AutoModerationPolicyVersion draft = requireDraft(siteId, policyId);
        if (!state.activePolicyVersionId().equals(draft.basedOnVersionId())) {
            throw conflict("Draft is based on an outdated active policy");
        }
        int version = state.lastPublishedVersion() + 1;
        AutoModerationPolicyVersion published = repository.publishDraft(
            siteId, policyId, expectedRevision, version
        ).orElseThrow(() -> conflict("Draft was changed in another session"));
        activate(siteId, state, published, version);
        return published;
    }

    @Transactional
    public AutoModerationPolicyVersion rollback(
        AuthenticatedUser currentUser,
        UUID siteId,
        UUID sourcePolicyId,
        UUID expectedActiveVersionId
    ) {
        ownershipService.assertSiteOwnedBy(currentUser, siteId);
        requireActiveOrInitialize(siteId);
        AutoModerationPolicyState state = lockState(siteId);
        assertExpectedActive(state, expectedActiveVersionId);
        if (repository.findDraft(siteId).isPresent()) {
            throw conflict("Delete or publish the current draft before rollback");
        }
        AutoModerationPolicyVersion source = repository.findById(siteId, sourcePolicyId)
            .filter(policy -> policy.lifecycle() == AutoModerationPolicyLifecycle.PUBLISHED)
            .orElseThrow(this::notFound);
        int version = state.lastPublishedVersion() + 1;
        AutoModerationPolicyVersion restored = repository.copyPublishedVersion(
            siteId, source.id(), currentUser.id(), version
        );
        activate(siteId, state, restored, version);
        return restored;
    }

    @Transactional
    public void publishLegacySettings(
        AuthenticatedUser currentUser,
        UUID siteId,
        AutoModerationSettings settings
    ) {
        ownershipService.assertSiteOwnedBy(currentUser, siteId);
        requireActiveOrInitialize(siteId);
        AutoModerationPolicyState state = lockState(siteId);
        if (repository.findDraft(siteId).isPresent()) {
            throw conflict("Delete or publish the current draft before changing legacy settings");
        }
        ActiveAutoModerationPolicy active = requireActive(siteId);
        if (!active.version().id().equals(state.activePolicyVersionId())) {
            throw conflict("Active policy changed in another session");
        }
        LegacyPolicy legacy = fromLegacy(settings);
        AutoModerationPolicyVersion draft = repository.createDraft(
            siteId,
            currentUser.id(),
            active.version().id(),
            legacy.enabled(),
            legacy.preset(),
            AutoModerationExecutionMode.LIVE,
            normalizeConfig(legacy.config())
        );
        int version = state.lastPublishedVersion() + 1;
        AutoModerationPolicyVersion published = repository.publishDraft(
            siteId, draft.id(), draft.revision(), version
        ).orElseThrow(() -> conflict("Legacy policy update conflicted"));
        activate(siteId, state, published, version);
    }

    @Transactional
    public AutoModerationEvaluation evaluateForComment(
        UUID siteId,
        String content,
        CommentStatus siteBaseline,
        CommentStatus shadowBaseline
    ) {
        ActiveAutoModerationPolicy active = requireActiveOrInitialize(siteId);
        if (!active.enabled()) {
            return null;
        }
        AutoModerationPolicyVersion effective = activeVersion(active);
        return evaluate(content, effective, siteBaseline, shadowBaseline);
    }

    private AutoModerationEvaluation evaluate(
        String content,
        AutoModerationPolicyVersion policy,
        CommentStatus siteBaseline,
        CommentStatus shadowBaseline
    ) {
        AutoModerationPolicyConfig config = policy.config();
        AutoModerationDecision legacy = evaluator.review(
            content,
            evaluatorSettings(policy),
            siteBaseline
        );
        List<AutoModerationSignal> signals = adjustLinkSignalScores(legacy.signals(), config).stream()
            .map(signal -> new AutoModerationSignal(
                signal.category(), signal.score(), safeMessage(signal.category())
            ))
            .toList();
        int score = signals.stream().mapToInt(AutoModerationSignal::score).sum();
        AutoModerationDecisionType decision = AutoModerationDecisionRules.classify(score, config);
        CommentStatus liveStatus = switch (decision) {
            case SPAM -> CommentStatus.SPAM;
            case REVIEW -> CommentStatus.PENDING;
            case APPROVE -> config.cleanAction() == AutoModerationCleanAction.APPROVE
                ? CommentStatus.APPROVED
                : siteBaseline;
        };
        boolean applied = policy.enabled() && policy.executionMode() == AutoModerationExecutionMode.LIVE;
        CommentStatus effectiveStatus = applied ? liveStatus : shadowBaseline;
        String reason = decision != AutoModerationDecisionType.APPROVE
            ? buildReason(signals)
            : null;
        return new AutoModerationEvaluation(
            policy.id(),
            policy.executionMode(),
            score,
            decision,
            siteBaseline,
            effectiveStatus,
            applied,
            reason,
            signals,
            clock.instant()
        );
    }

    private List<AutoModerationSignal> adjustLinkSignalScores(
        List<AutoModerationSignal> source,
        AutoModerationPolicyConfig config
    ) {
        List<AutoModerationSignal> adjusted = new ArrayList<>(source.size());
        for (AutoModerationSignal signal : source) {
            if (signal.category().equals("BLOCKED_LINK") && config.linkAction() == AutoModerationLinkAction.SPAM) {
                adjusted.add(new AutoModerationSignal(signal.category(), config.spamThreshold(), signal.reason()));
            } else if (signal.category().equals("LINK_FLOOD")
                && config.linkAction() == AutoModerationLinkAction.REVIEW) {
                adjusted.add(new AutoModerationSignal(
                    signal.category(), Math.max(signal.score(), config.reviewThreshold()), signal.reason()
                ));
            } else {
                adjusted.add(signal);
            }
        }
        return List.copyOf(adjusted);
    }

    private AutoModerationSettings evaluatorSettings(AutoModerationPolicyVersion policy) {
        AutoModerationStrictness strictness = switch (policy.preset()) {
            case OPEN -> AutoModerationStrictness.RELAXED;
            case STRICT -> AutoModerationStrictness.STRICT;
            case BALANCED -> AutoModerationStrictness.BALANCED;
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
        AutoModerationPolicyConfig config = policy.config();
        return new AutoModerationSettings(
            true,
            strictness,
            config.blockedWords(),
            config.linkAction() == AutoModerationLinkAction.REVIEW,
            config.linkAction() == AutoModerationLinkAction.SPAM,
            config.maxLinks()
        );
    }

    private AutoModerationPolicyConfig normalizeConfig(AutoModerationPolicyConfig config) {
        if (config.reviewThreshold() < 0
            || config.spamThreshold() > AutoModerationPolicyConfig.MAX_THRESHOLD
            || config.reviewThreshold() >= config.spamThreshold()) {
            throw badRequest("reviewThreshold must be lower than spamThreshold");
        }
        if (config.maxLinks() < 0 || config.maxLinks() > AutoModerationPolicyConfig.MAX_LINKS) {
            throw badRequest("maxLinks is out of range");
        }
        if (config.cleanAction() == null || config.linkAction() == null) {
            throw badRequest("Policy actions are required");
        }
        if (config.blockedWords().size() > AutoModerationPolicyConfig.MAX_BLOCKED_WORDS) {
            throw badRequest("Too many blocked words");
        }
        List<String> normalized = new ArrayList<>(config.blockedWords().size());
        Set<String> seen = new HashSet<>();
        for (String value : config.blockedWords()) {
            String word = value != null ? value.trim() : "";
            if (word.isBlank() || word.chars().anyMatch(character -> Character.isISOControl(character))) {
                throw badRequest("Blocked words must not be blank or contain control characters");
            }
            if (word.length() > AutoModerationPolicyConfig.MAX_BLOCKED_WORD_LENGTH) {
                throw badRequest("Blocked word is too long");
            }
            if (!seen.add(word.toLowerCase(Locale.ROOT))) {
                throw badRequest("Blocked words must be unique");
            }
            normalized.add(word);
        }
        return new AutoModerationPolicyConfig(
            config.reviewThreshold(),
            config.spamThreshold(),
            config.cleanAction(),
            config.linkAction(),
            config.maxLinks(),
            normalized
        );
    }

    private LegacyPolicy fromLegacy(AutoModerationSettings settings) {
        AutoModerationStrictness strictness = settings.enabled() && settings.strictness() != AutoModerationStrictness.OFF
            ? settings.strictness()
            : AutoModerationStrictness.BALANCED;
        AutoModerationPreset basePreset = switch (strictness) {
            case RELAXED -> AutoModerationPreset.OPEN;
            case STRICT -> AutoModerationPreset.STRICT;
            case OFF, BALANCED -> AutoModerationPreset.BALANCED;
        };
        AutoModerationPolicyConfig presetConfig = AutoModerationPolicyConfig.preset(basePreset);
        AutoModerationLinkAction linkAction = settings.blockLinks()
            ? AutoModerationLinkAction.SPAM
            : settings.holdLinks() ? AutoModerationLinkAction.REVIEW : AutoModerationLinkAction.ALLOW;
        int maxLinks = strictness == AutoModerationStrictness.STRICT && settings.holdLinks()
            ? 0
            : settings.maxLinks();
        boolean custom = !settings.blockedWords().isEmpty()
            || linkAction != presetConfig.linkAction()
            || maxLinks != presetConfig.maxLinks();
        return new LegacyPolicy(
            settings.active(),
            custom ? AutoModerationPreset.CUSTOM : basePreset,
            new AutoModerationPolicyConfig(
                presetConfig.reviewThreshold(),
                presetConfig.spamThreshold(),
                presetConfig.cleanAction(),
                linkAction,
                maxLinks,
                settings.blockedWords()
            )
        );
    }

    private AutoModerationPolicyVersion activeVersion(ActiveAutoModerationPolicy active) {
        AutoModerationPolicyVersion version = active.version();
        return new AutoModerationPolicyVersion(
            version.id(), version.siteId(), version.version(), version.revision(), version.lifecycle(),
            active.enabled(), version.preset(), active.executionMode(), version.config(), version.basedOnVersionId(),
            version.createdAt(), version.updatedAt(), version.publishedAt()
        );
    }

    private ActiveAutoModerationPolicy requireActive(UUID siteId) {
        return repository.findActive(siteId).orElseThrow(this::notFound);
    }

    private ActiveAutoModerationPolicy requireActiveOrInitialize(UUID siteId) {
        Optional<ActiveAutoModerationPolicy> active = repository.findActive(siteId);
        if (active.isPresent()) {
            return active.orElseThrow();
        }
        repository.initializeFromLegacy(siteId);
        return requireActive(siteId);
    }

    private AutoModerationPolicyVersion requireDraft(UUID siteId, UUID policyId) {
        return repository.findById(siteId, policyId)
            .filter(policy -> policy.lifecycle() == AutoModerationPolicyLifecycle.DRAFT)
            .orElseThrow(this::notFound);
    }

    private AutoModerationPolicyState lockState(UUID siteId) {
        return repository.lockState(siteId).orElseThrow(this::notFound);
    }

    private void assertExpectedActive(AutoModerationPolicyState state, UUID expectedActiveVersionId) {
        if (expectedActiveVersionId == null || !state.activePolicyVersionId().equals(expectedActiveVersionId)) {
            throw conflict("Active policy changed in another session");
        }
    }

    private void activate(
        UUID siteId,
        AutoModerationPolicyState state,
        AutoModerationPolicyVersion policy,
        int version
    ) {
        if (!repository.activate(siteId, state.activePolicyVersionId(), policy, version)) {
            throw conflict("Active policy changed in another session");
        }
        repository.synchronizeLegacySettings(siteId, policy);
    }

    private String buildReason(List<AutoModerationSignal> signals) {
        String reason = signals.stream()
            .limit(6)
            .map(AutoModerationSignal::reason)
            .distinct()
            .reduce((left, right) -> left + "; " + right)
            .map(value -> "Автомодерация: " + value)
            .orElse(null);
        return reason == null || reason.length() <= MAX_REASON_LENGTH
            ? reason
            : reason.substring(0, MAX_REASON_LENGTH - 3) + "...";
    }

    private String safeMessage(String code) {
        return switch (code) {
            case "CUSTOM_BLOCKED_WORD" -> "Найдено стоп-слово владельца";
            case "BLOCKED_LINK" -> "Ссылки запрещены политикой";
            case "LINK_FLOOD" -> "Превышен допустимый лимит ссылок";
            case "CONTAINS_LINK" -> "Комментарий содержит ссылку";
            case "SPAM_PHRASE" -> "Найден спам-маркер";
            case "TOXICITY" -> "Найден токсичный маркер";
            case "REPEATED_CHARACTERS" -> "Много повторяющихся символов";
            case "OBFUSCATION" -> "Обнаружена подозрительная обфускация текста";
            case "AGGRESSIVE_CAPS" -> "Слишком много текста в верхнем регистре";
            case "SUSPICIOUS_CONTACT" -> "Обнаружен подозрительный контакт";
            default -> "Обнаружен сигнал автомодерации";
        };
    }

    private ApplicationException notFound() {
        return new ApplicationException(ApiErrorCode.NOT_FOUND, "Resource not found");
    }

    private ApplicationException conflict(String message) {
        return new ApplicationException(ApiErrorCode.BUSINESS_ERROR, message);
    }

    private ApplicationException badRequest(String message) {
        return new ApplicationException(ApiErrorCode.BAD_REQUEST, message);
    }

    private record LegacyPolicy(
        boolean enabled,
        AutoModerationPreset preset,
        AutoModerationPolicyConfig config
    ) {
    }
}
