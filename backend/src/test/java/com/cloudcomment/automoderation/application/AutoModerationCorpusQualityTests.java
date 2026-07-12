package com.cloudcomment.automoderation.application;

import com.cloudcomment.automoderation.domain.AutoModerationDecisionType;
import com.cloudcomment.automoderation.domain.AutoModerationPolicyConfig;
import com.cloudcomment.automoderation.domain.AutoModerationPreset;
import com.cloudcomment.comment.application.AutoModerationService;
import com.cloudcomment.comment.domain.CommentStatus;
import com.cloudcomment.site.domain.AutoModerationSettings;
import com.cloudcomment.site.domain.AutoModerationStrictness;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class AutoModerationCorpusQualityTests {

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("\\+?\\d[\\d ()-]{8,}\\d");

    private final AutoModerationService evaluator = new AutoModerationService();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exactPresetBoundariesRemainDeterministic() {
        assertBoundaries(AutoModerationPreset.OPEN, 69, 70, 129, 130);
        assertBoundaries(AutoModerationPreset.BALANCED, 44, 45, 89, 90);
        assertBoundaries(AutoModerationPreset.STRICT, 24, 25, 84, 85);
    }

    @Test
    void russianAndEnglishCorporaMeetQualityAndParityGates() {
        List<CorpusSeed> russianSeeds = load("/automod/corpus-ru.jsonl");
        List<CorpusSeed> englishSeeds = load("/automod/corpus-en.jsonl");
        CorpusMetrics russian = evaluateCorpus("ru", russianSeeds, AutoModerationStrictness.BALANCED);
        CorpusMetrics english = evaluateCorpus("en", englishSeeds, AutoModerationStrictness.BALANCED);

        assertBalancedGates(russian);
        assertBalancedGates(english);
        assertThat(Math.abs(russian.macroF1() - english.macroF1()))
            .as("RU/EN macro-F1 delta")
            .isLessThanOrEqualTo(0.05);

        for (CorpusMetrics open : List.of(
            evaluateCorpus("ru-open", russianSeeds, AutoModerationStrictness.RELAXED),
            evaluateCorpus("en-open", englishSeeds, AutoModerationStrictness.RELAXED)
        )) {
            assertThat(open.spamPrecision()).as(open.summary()).isGreaterThanOrEqualTo(0.99);
            assertThat(open.safeToSpamRate()).as(open.summary()).isZero();
        }
        for (CorpusMetrics strict : List.of(
            evaluateCorpus("ru-strict", russianSeeds, AutoModerationStrictness.STRICT),
            evaluateCorpus("en-strict", englishSeeds, AutoModerationStrictness.STRICT)
        )) {
            assertThat(strict.approvePrecision()).as(strict.summary()).isGreaterThanOrEqualTo(0.99);
            assertThat(strict.spamToApproveRate()).as(strict.summary()).isZero();
        }
    }

    @Test
    void corpusIsSyntheticPrivateAndPresetSeverityIsMonotonic() {
        for (String resource : List.of("/automod/corpus-ru.jsonl", "/automod/corpus-en.jsonl")) {
            List<CorpusItem> items = expand(load(resource));
            List<CorpusSeed> seeds = load(resource);
            for (Label label : Label.values()) {
                assertThat(seeds.stream().filter(seed -> seed.label().equals(label.name())).count())
                    .as(resource + " semantic templates for " + label)
                    .isGreaterThanOrEqualTo(20);
            }
            assertThat(items).hasSize(180);
            assertThat(items).filteredOn(item -> item.label() == Label.SAFE).hasSize(60);
            assertThat(items).filteredOn(item -> item.label() == Label.REVIEW).hasSize(60);
            assertThat(items).filteredOn(item -> item.label() == Label.SPAM).hasSize(60);
            assertThat(items).filteredOn(item -> item.tags().contains("adversarial")).hasSizeGreaterThanOrEqualTo(20);
            assertThat(items).filteredOn(item -> item.tags().contains("legitimate")).hasSizeGreaterThanOrEqualTo(15);
            for (CorpusItem item : items) {
                assertThat(item.content().chars().noneMatch(Character::isISOControl)).as(item.id()).isTrue();
                assertThat(EMAIL.matcher(item.content()).find()).as(item.id()).isFalse();
                assertThat(PHONE.matcher(item.content()).find()).as(item.id()).isFalse();

                int open = severity(review(item.content(), AutoModerationStrictness.RELAXED));
                int balanced = severity(review(item.content(), AutoModerationStrictness.BALANCED));
                int strict = severity(review(item.content(), AutoModerationStrictness.STRICT));
                assertThat(open).as(item.id()).isLessThanOrEqualTo(balanced);
                assertThat(balanced).as(item.id()).isLessThanOrEqualTo(strict);
            }
        }
    }

    private void assertBoundaries(AutoModerationPreset preset, int approve, int review, int reviewHigh, int spam) {
        AutoModerationPolicyConfig config = AutoModerationPolicyConfig.preset(preset);
        assertThat(AutoModerationDecisionRules.classify(approve, config))
            .isEqualTo(AutoModerationDecisionType.APPROVE);
        assertThat(AutoModerationDecisionRules.classify(review, config))
            .isEqualTo(AutoModerationDecisionType.REVIEW);
        assertThat(AutoModerationDecisionRules.classify(reviewHigh, config))
            .isEqualTo(AutoModerationDecisionType.REVIEW);
        assertThat(AutoModerationDecisionRules.classify(spam, config))
            .isEqualTo(AutoModerationDecisionType.SPAM);
    }

    private CorpusMetrics evaluateCorpus(
        String language,
        List<CorpusSeed> seeds,
        AutoModerationStrictness strictness
    ) {
        List<CorpusItem> items = expand(seeds);
        Map<Label, Map<Label, Integer>> confusion = new EnumMap<>(Label.class);
        for (Label expected : Label.values()) {
            confusion.put(expected, new EnumMap<>(Label.class));
            for (Label actual : Label.values()) {
                confusion.get(expected).put(actual, 0);
            }
        }
        List<String> failures = new ArrayList<>();
        for (CorpusItem item : items) {
            Label actual = label(review(item.content(), strictness));
            confusion.get(item.label()).merge(actual, 1, Integer::sum);
            if (actual != item.label()) {
                failures.add(item.id());
            }
        }
        return CorpusMetrics.from(language, confusion, failures);
    }

    private void assertBalancedGates(CorpusMetrics metrics) {
        assertThat(metrics.total()).as(metrics.language() + " corpus size").isGreaterThanOrEqualTo(180);
        assertThat(metrics.approvePrecision()).as(metrics.summary()).isGreaterThanOrEqualTo(0.98);
        assertThat(metrics.spamPrecision()).as(metrics.summary()).isGreaterThanOrEqualTo(0.97);
        assertThat(metrics.spamToApproveRate()).as(metrics.summary()).isLessThanOrEqualTo(0.05);
        assertThat(metrics.safeToSpamRate()).as(metrics.summary()).isLessThanOrEqualTo(0.02);
        assertThat(metrics.reviewRecall()).as(metrics.summary()).isGreaterThanOrEqualTo(0.75);
        assertThat(metrics.macroF1()).as(metrics.summary()).isGreaterThanOrEqualTo(0.85);
    }

    private CommentStatus review(String content, AutoModerationStrictness strictness) {
        return evaluator.review(
            content,
            new AutoModerationSettings(true, strictness, List.of(), true, false, 2),
            CommentStatus.APPROVED
        ).status();
    }

    private int severity(CommentStatus status) {
        return switch (status) {
            case APPROVED -> 0;
            case PENDING -> 1;
            case SPAM -> 2;
            case REJECTED, HIDDEN -> throw new IllegalArgumentException("Unexpected automatic status " + status);
        };
    }

    private Label label(CommentStatus status) {
        return switch (status) {
            case APPROVED -> Label.SAFE;
            case PENDING -> Label.REVIEW;
            case SPAM -> Label.SPAM;
            case REJECTED, HIDDEN -> throw new IllegalArgumentException("Unexpected automatic status " + status);
        };
    }

    private List<CorpusSeed> load(String resource) {
        InputStream stream = AutoModerationCorpusQualityTests.class.getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalStateException("Missing corpus resource " + resource);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines()
                .filter(line -> !line.isBlank())
                .map(line -> objectMapper.readValue(line, CorpusSeed.class))
                .toList();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read corpus " + resource, exception);
        }
    }

    private List<CorpusItem> expand(List<CorpusSeed> seeds) {
        return seeds.stream()
            .flatMap(seed -> java.util.stream.IntStream.range(0, seed.count())
                .mapToObj(index -> new CorpusItem(
                    seed.id() + "-" + index,
                    Label.valueOf(seed.label().toUpperCase(Locale.ROOT)),
                    seed.template().formatted(suffix(index)),
                    seed.tags()
                )))
            .toList();
    }

    private String suffix(int index) {
        return switch (index % 3) {
            case 0 -> "alpha";
            case 1 -> "beta";
            default -> "gamma";
        };
    }

    private enum Label {
        SAFE,
        REVIEW,
        SPAM
    }

    private record CorpusSeed(String id, String label, String template, int count, List<String> tags) {

        private CorpusSeed {
            tags = tags != null ? List.copyOf(tags) : List.of();
        }
    }

    private record CorpusItem(String id, Label label, String content, List<String> tags) {
    }

    private record CorpusMetrics(
        String language,
        int total,
        double approvePrecision,
        double spamPrecision,
        double spamToApproveRate,
        double safeToSpamRate,
        double reviewRecall,
        double macroF1,
        List<String> failureIds
    ) {

        static CorpusMetrics from(
            String language,
            Map<Label, Map<Label, Integer>> confusion,
            List<String> failureIds
        ) {
            int total = confusion.values().stream()
                .flatMap(row -> row.values().stream())
                .mapToInt(Integer::intValue)
                .sum();
            double approvePrecision = precision(confusion, Label.SAFE);
            double spamPrecision = precision(confusion, Label.SPAM);
            double reviewRecall = recall(confusion, Label.REVIEW);
            double macroF1 = java.util.Arrays.stream(Label.values())
                .mapToDouble(label -> f1(confusion, label))
                .average()
                .orElse(0);
            double spamToApprove = ratio(confusion.get(Label.SPAM).get(Label.SAFE), rowTotal(confusion, Label.SPAM));
            double safeToSpam = ratio(confusion.get(Label.SAFE).get(Label.SPAM), rowTotal(confusion, Label.SAFE));
            return new CorpusMetrics(
                language, total, approvePrecision, spamPrecision, spamToApprove, safeToSpam,
                reviewRecall, macroF1, List.copyOf(failureIds)
            );
        }

        String summary() {
            return language + " confusion failures=" + failureIds;
        }

        private static double precision(Map<Label, Map<Label, Integer>> confusion, Label label) {
            int truePositive = confusion.get(label).get(label);
            int predicted = confusion.values().stream().mapToInt(row -> row.get(label)).sum();
            return ratio(truePositive, predicted);
        }

        private static double recall(Map<Label, Map<Label, Integer>> confusion, Label label) {
            return ratio(confusion.get(label).get(label), rowTotal(confusion, label));
        }

        private static double f1(Map<Label, Map<Label, Integer>> confusion, Label label) {
            double precision = precision(confusion, label);
            double recall = recall(confusion, label);
            return precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
        }

        private static int rowTotal(Map<Label, Map<Label, Integer>> confusion, Label label) {
            return confusion.get(label).values().stream().mapToInt(Integer::intValue).sum();
        }

        private static double ratio(int numerator, int denominator) {
            return denominator == 0 ? 1 : (double) numerator / denominator;
        }
    }
}
