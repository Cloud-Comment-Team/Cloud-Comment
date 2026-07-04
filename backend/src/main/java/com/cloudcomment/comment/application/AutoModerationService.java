package com.cloudcomment.comment.application;

import com.cloudcomment.comment.domain.CommentStatus;
import com.cloudcomment.site.domain.AutoModerationSettings;
import com.cloudcomment.site.domain.AutoModerationStrictness;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AutoModerationService {

    private static final Pattern LINK_PATTERN = Pattern.compile(
        "(?i)(?:https?://|www\\.)\\S+|\\b[\\p{Alnum}._%+-]+\\.(?:ru|com|net|org|io|dev|app|shop|site|online)\\b"
    );
    private static final Pattern REPEATED_CHARACTER_PATTERN = Pattern.compile("(?iu)([\\p{L}\\p{N}])\\1{5,}");

    private static final List<String> DEFAULT_SPAM_PHRASES = List.of(
        "casino",
        "viagra",
        "free money",
        "crypto giveaway",
        "казино",
        "ставки",
        "букмекер",
        "заработок без вложений",
        "купить подписчиков",
        "накрутка",
        "быстрый заработок"
    );

    private static final List<String> DEFAULT_TOXIC_TERMS = List.of(
        "идиот",
        "дурак",
        "тупой",
        "ненавижу",
        "scam"
    );

    public AutoModerationDecision review(
        String content,
        AutoModerationSettings settings,
        CommentStatus fallbackStatus
    ) {
        AutoModerationSettings activeSettings = settings != null ? settings : AutoModerationSettings.defaultSettings();
        if (!activeSettings.active()) {
            return new AutoModerationDecision(fallbackStatus, null);
        }

        String normalizedContent = content.toLowerCase(Locale.ROOT);
        List<String> reasons = new ArrayList<>();
        int score = 0;

        List<String> matchedCustomWords = activeSettings.blockedWords().stream()
            .filter(word -> containsToken(normalizedContent, word))
            .toList();
        if (!matchedCustomWords.isEmpty()) {
            reasons.add("custom blocked words: " + String.join(", ", matchedCustomWords));
            score += 100;
        }

        int linkCount = countLinks(content);
        if (linkCount > 0 && activeSettings.blockLinks()) {
            reasons.add("links are blocked");
            score += 100;
        } else if (activeSettings.holdLinks() && linkCount > activeSettings.maxLinks()) {
            reasons.add("too many links: " + linkCount);
            score += activeSettings.strictness() == AutoModerationStrictness.STRICT ? 70 : 45;
        } else if (activeSettings.holdLinks() && linkCount > 0) {
            reasons.add("contains link");
            score += activeSettings.strictness() == AutoModerationStrictness.STRICT ? 35 : 18;
        }

        List<String> spamSignals = DEFAULT_SPAM_PHRASES.stream()
            .filter(phrase -> normalizedContent.contains(phrase))
            .toList();
        if (!spamSignals.isEmpty()) {
            reasons.add("spam signals: " + String.join(", ", spamSignals));
            score += 45 + spamSignals.size() * 10;
        }

        List<String> toxicitySignals = DEFAULT_TOXIC_TERMS.stream()
            .filter(term -> normalizedContent.contains(term))
            .toList();
        if (!toxicitySignals.isEmpty()) {
            reasons.add("toxicity signals: " + String.join(", ", toxicitySignals));
            score += activeSettings.strictness() == AutoModerationStrictness.RELAXED ? 20 : 35;
        }

        if (REPEATED_CHARACTER_PATTERN.matcher(content).find()) {
            reasons.add("repeated characters");
            score += 20;
        }

        if (hasAggressiveCaps(content)) {
            reasons.add("too much uppercase text");
            score += activeSettings.strictness() == AutoModerationStrictness.STRICT ? 30 : 18;
        }

        CommentStatus status = chooseStatus(score, fallbackStatus, activeSettings.strictness());
        String reason = reasons.isEmpty() || status == CommentStatus.APPROVED
            ? null
            : limitReason(String.join("; ", reasons));
        return new AutoModerationDecision(status, reason);
    }

    private CommentStatus chooseStatus(
        int score,
        CommentStatus fallbackStatus,
        AutoModerationStrictness strictness
    ) {
        Thresholds thresholds = thresholds(strictness);
        if (score >= thresholds.spam()) {
            return CommentStatus.SPAM;
        }
        if (score >= thresholds.hold()) {
            return CommentStatus.PENDING;
        }
        return fallbackStatus;
    }

    private Thresholds thresholds(AutoModerationStrictness strictness) {
        return switch (strictness) {
            case STRICT -> new Thresholds(25, 85);
            case RELAXED -> new Thresholds(65, 120);
            case OFF -> new Thresholds(Integer.MAX_VALUE, Integer.MAX_VALUE);
            case BALANCED -> new Thresholds(45, 100);
        };
    }

    private boolean containsToken(String normalizedContent, String word) {
        String normalizedWord = word.trim().toLowerCase(Locale.ROOT);
        return !normalizedWord.isBlank() && normalizedContent.contains(normalizedWord);
    }

    private int countLinks(String content) {
        Matcher matcher = LINK_PATTERN.matcher(content);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private boolean hasAggressiveCaps(String content) {
        int letters = 0;
        int uppercase = 0;
        for (int index = 0; index < content.length(); index++) {
            char character = content.charAt(index);
            if (Character.isLetter(character)) {
                letters++;
                if (Character.isUpperCase(character)) {
                    uppercase++;
                }
            }
        }
        return letters >= 18 && ((double) uppercase / letters) > 0.72;
    }

    private String limitReason(String reason) {
        return reason.length() <= 500 ? reason : reason.substring(0, 497) + "...";
    }

    private record Thresholds(int hold, int spam) {
    }
}
