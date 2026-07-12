package com.cloudcomment.comment.application;

import com.cloudcomment.comment.domain.CommentStatus;
import com.cloudcomment.site.domain.AutoModerationSettings;
import com.cloudcomment.site.domain.AutoModerationStrictness;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AutoModerationService {

    private static final int MAX_REASON_LENGTH = 500;
    private static final int MAX_SIGNALS_IN_REASON = 6;

    private static final Pattern LINK_PATTERN = Pattern.compile(
        "(?iu)(?:https?://|www\\.)\\S+|\\b[\\p{Alnum}._%+-]+\\.(?:ru|com|net|org|io|dev|app|shop|site|online|biz|info|xyz)\\b"
    );
    private static final Pattern REPEATED_CHARACTER_PATTERN = Pattern.compile("(?iu)([\\p{L}\\p{N}])\\1{4,}");
    private static final Pattern OBFUSCATED_WORD_PATTERN = Pattern.compile(
        "(?iu)(?:[\\p{L}\\p{N}][^\\p{L}\\p{N}]{1,3}){4,}[\\p{L}\\p{N}]"
    );
    private static final Pattern LEETSPEAK_PATTERN = Pattern.compile("[@$013457!|]");
    private static final Pattern CONTACT_PATTERN = Pattern.compile(
        "(?iu)(telegram|телеграм|whatsapp|ватсап|viber|вайбер|t\\.me/|wa\\.me/|@[\\p{Alnum}_]{4,}|\\+?\\d[\\d\\s().-]{8,}\\d)"
    );

    private static final List<RulePhrase> DEFAULT_SPAM_PHRASES = List.of(
        new RulePhrase("casino", "казино / азартные игры"),
        new RulePhrase("казино", "казино / азартные игры"),
        new RulePhrase("ставки", "ставки и букмекерские предложения"),
        new RulePhrase("букмекер", "букмекерские предложения"),
        new RulePhrase("viagra", "фарма-спам"),
        new RulePhrase("free money", "быстрый заработок"),
        new RulePhrase("crypto giveaway", "криптовалютная раздача"),
        new RulePhrase("крипто раздача", "криптовалютная раздача"),
        new RulePhrase("быстрый заработок", "быстрый заработок"),
        new RulePhrase("заработок без вложений", "заработок без вложений"),
        new RulePhrase("пассивный доход", "сомнительный заработок"),
        new RulePhrase("накрутка", "накрутка активности"),
        new RulePhrase("купить подписчиков", "накрутка подписчиков"),
        new RulePhrase("подписчики дешево", "накрутка подписчиков"),
        new RulePhrase("займ без отказа", "финансовый спам"),
        new RulePhrase("скидка 90", "агрессивная промо-скидка")
    );

    private static final List<RulePhrase> DEFAULT_TOXIC_TERMS = List.of(
        new RulePhrase("идиот", "оскорбление"),
        new RulePhrase("дурак", "оскорбление"),
        new RulePhrase("тупой", "оскорбление"),
        new RulePhrase("ненавижу", "агрессивная токсичность"),
        new RulePhrase("мразь", "грубая токсичность"),
        new RulePhrase("лохотрон", "обвинение в мошенничестве"),
        new RulePhrase("scam", "обвинение в мошенничестве")
    );

    public AutoModerationDecision review(
        String content,
        AutoModerationSettings settings,
        CommentStatus fallbackStatus
    ) {
        AutoModerationSettings activeSettings = settings != null ? settings : AutoModerationSettings.defaultSettings();
        if (!activeSettings.active()) {
            return new AutoModerationDecision(fallbackStatus, null, 0, List.of());
        }

        NormalizedText normalizedText = normalize(content);
        List<AutoModerationSignal> signals = new ArrayList<>();

        activeSettings.blockedWords().stream()
            .filter(word -> containsPhrase(normalizedText, word))
            .limit(8)
            .forEach(word -> signals.add(new AutoModerationSignal(
                "CUSTOM_BLOCKED_WORD",
                120,
                "Найдено стоп-слово владельца"
            )));

        int linkCount = countLinks(content);
        if (linkCount > 0 && activeSettings.blockLinks()) {
            signals.add(new AutoModerationSignal(
                "BLOCKED_LINK",
                130,
                "Ссылки запрещены настройками сайта"
            ));
        } else if (activeSettings.holdLinks() && linkCount > activeSettings.maxLinks()) {
            signals.add(new AutoModerationSignal(
                "LINK_FLOOD",
                75,
                "Слишком много ссылок: " + linkCount + " при лимите " + activeSettings.maxLinks()
            ));
        } else if (activeSettings.holdLinks() && linkCount > 0) {
            signals.add(new AutoModerationSignal(
                "CONTAINS_LINK",
                activeSettings.strictness() == AutoModerationStrictness.STRICT ? 35 : 20,
                "Комментарий содержит ссылку"
            ));
        }

        DEFAULT_SPAM_PHRASES.stream()
            .filter(rule -> containsPhrase(normalizedText, rule.phrase()))
            .limit(5)
            .forEach(rule -> signals.add(new AutoModerationSignal(
                "SPAM_PHRASE",
                55,
                "Спам-маркер: " + rule.reason()
            )));

        DEFAULT_TOXIC_TERMS.stream()
            .filter(rule -> containsPhrase(normalizedText, rule.phrase()))
            .limit(4)
            .forEach(rule -> signals.add(new AutoModerationSignal(
                "TOXICITY",
                activeSettings.strictness() == AutoModerationStrictness.RELAXED ? 25 : 45,
                "Токсичный маркер: " + rule.reason()
            )));

        if (REPEATED_CHARACTER_PATTERN.matcher(normalizedText.source()).find()) {
            signals.add(new AutoModerationSignal(
                "REPEATED_CHARACTERS",
                20,
                "Много повторяющихся символов"
            ));
        }

        if (hasObfuscation(content, normalizedText)) {
            signals.add(new AutoModerationSignal(
                "OBFUSCATION",
                25,
                "Подозрительная обфускация текста"
            ));
        }

        if (hasAggressiveCaps(content)) {
            signals.add(new AutoModerationSignal(
                "AGGRESSIVE_CAPS",
                activeSettings.strictness() == AutoModerationStrictness.STRICT ? 30 : 18,
                "Слишком много текста капсом"
            ));
        }

        if (CONTACT_PATTERN.matcher(normalizedText.source()).find()) {
            signals.add(new AutoModerationSignal(
                "SUSPICIOUS_CONTACT",
                45,
                "Подозрительный контакт или перевод в мессенджер"
            ));
        }

        List<AutoModerationSignal> normalizedSignals = signals.stream()
            .sorted(Comparator.comparingInt(AutoModerationSignal::score).reversed())
            .toList();
        int score = normalizedSignals.stream().mapToInt(AutoModerationSignal::score).sum();
        CommentStatus status = chooseStatus(score, fallbackStatus, activeSettings.strictness());
        String reason = normalizedSignals.isEmpty() || status == CommentStatus.APPROVED
            ? null
            : buildReason(normalizedSignals);

        return new AutoModerationDecision(status, reason, score, normalizedSignals);
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
            case RELAXED -> new Thresholds(70, 130);
            case OFF -> new Thresholds(Integer.MAX_VALUE, Integer.MAX_VALUE);
            case BALANCED -> new Thresholds(45, 90);
        };
    }

    private boolean containsPhrase(NormalizedText normalizedText, String phrase) {
        NormalizedText normalizedPhrase = normalize(phrase);
        if (normalizedPhrase.words().isBlank()) {
            return false;
        }

        boolean wordsMatch = normalizedPhrase.words().contains(" ")
            ? normalizedText.words().contains(normalizedPhrase.words())
            : containsWholeToken(normalizedText.words(), normalizedPhrase.words());
        boolean compactMatch = hasCompactObfuscation(normalizedText.source())
            && normalizedText.compact().contains(normalizedPhrase.compact());
        return wordsMatch || compactMatch;
    }

    private boolean containsWholeToken(String normalizedWords, String normalizedPhrase) {
        return Pattern.compile("(?iu)(^|\\s)" + Pattern.quote(normalizedPhrase) + "(\\s|$)")
            .matcher(normalizedWords)
            .find();
    }

    private NormalizedText normalize(String content) {
        String source = content == null ? "" : content;
        StringBuilder words = new StringBuilder(source.length());
        StringBuilder compact = new StringBuilder(source.length());
        char previous = 0;
        int runLength = 0;

        String lowered = source.toLowerCase(Locale.ROOT).replace('ё', 'е');
        for (int index = 0; index < lowered.length(); index++) {
            char character = normalizeObfuscatedCharacter(lowered.charAt(index));
            if (Character.isLetterOrDigit(character)) {
                if (character == previous) {
                    runLength++;
                } else {
                    previous = character;
                    runLength = 1;
                }

                if (runLength <= 2) {
                    words.append(character);
                    compact.append(character);
                }
            } else {
                previous = 0;
                runLength = 0;
                if (words.isEmpty() || words.charAt(words.length() - 1) != ' ') {
                    words.append(' ');
                }
            }
        }

        return new NormalizedText(
            source,
            words.toString().trim().replaceAll("\\s+", " "),
            compact.toString()
        );
    }

    private char normalizeObfuscatedCharacter(char character) {
        return switch (character) {
            case '@', '4' -> 'a';
            case '$', '5' -> 's';
            case '0' -> 'o';
            case '1', '!', '|' -> 'i';
            case '3' -> 'e';
            case '7' -> 't';
            default -> character;
        };
    }

    private int countLinks(String content) {
        Matcher matcher = LINK_PATTERN.matcher(content != null ? content : "");
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private boolean hasObfuscation(String content, NormalizedText normalizedText) {
        String source = content != null ? content : "";
        if (hasCompactObfuscation(source)) {
            return true;
        }
        return DEFAULT_SPAM_PHRASES.stream()
            .map(RulePhrase::phrase)
            .map(this::normalize)
            .anyMatch(rule -> !rule.compact().equals(rule.words())
                && normalizedText.compact().contains(rule.compact()));
    }

    private boolean hasCompactObfuscation(String source) {
        String value = source != null ? source : "";
        return OBFUSCATED_WORD_PATTERN.matcher(value).find()
            || LEETSPEAK_PATTERN.matcher(value).find();
    }

    private boolean hasAggressiveCaps(String content) {
        String source = content != null ? content : "";
        int letters = 0;
        int uppercase = 0;
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            if (Character.isLetter(character)) {
                letters++;
                if (Character.isUpperCase(character)) {
                    uppercase++;
                }
            }
        }
        return letters >= 18 && ((double) uppercase / letters) > 0.72;
    }

    private String buildReason(List<AutoModerationSignal> signals) {
        String reason = signals.stream()
            .limit(MAX_SIGNALS_IN_REASON)
            .map(AutoModerationSignal::reason)
            .distinct()
            .reduce((left, right) -> left + "; " + right)
            .map(value -> "Автомодерация: " + value)
            .orElse(null);
        return reason == null || reason.length() <= MAX_REASON_LENGTH
            ? reason
            : reason.substring(0, MAX_REASON_LENGTH - 3) + "...";
    }

    private record Thresholds(int hold, int spam) {
    }

    private record NormalizedText(String source, String words, String compact) {
    }

    private record RulePhrase(String phrase, String reason) {
    }
}
