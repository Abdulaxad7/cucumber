package org.scenarioservice.gherkin;

import io.cucumber.gherkin.GherkinDialect;
import io.cucumber.gherkin.GherkinDialectProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec A10 as corrected on 2026-10-18 (verified against gherkin 28.0.0's dialect list; this test sits in the
 * {@code gherkin} package, the only one that uses {@code io.cucumber}, P14): every language code fits
 * {@code features.language} ({@code varchar(10)}). Keywords are stored in {@code keyword_text text}, because the
 * {@code en-tx} Examples keyword has 53 code points ({@code repository/PersistenceModelTest} round-trips it); every
 * keyword is storable text (no U+0000, no lone surrogate; D8).
 */
class GherkinDialectsTest {

    @Test
    @DisplayName("A10: language codes are at most 10 characters, and every keyword is storable text")
    void everyDialectFitsTheColumns() {
        GherkinDialectProvider provider = new GherkinDialectProvider();
        List<GherkinDialect> dialects = provider.getLanguages().stream()
                .map(language -> provider.getDialect(language).orElseThrow()).toList();

        assertThat(dialects).hasSizeGreaterThan(70);
        assertThat(dialects).allSatisfy(dialect -> assertThat(dialect.getLanguage().length())
                .as(dialect.getLanguage()).isLessThanOrEqualTo(10));
        List<Keyword> keywords = dialects.stream().flatMap(GherkinDialectsTest::keywords).toList();
        assertThat(keywords).allSatisfy(keyword -> assertThat(keyword.text()).doesNotContain("\u0000")
                .satisfies(text -> assertThat(text.codePoints()
                        .noneMatch(point -> point >= 0xD800 && point <= 0xDFFF)).isTrue()));
        assertThat(keywords.stream().mapToInt(keyword -> keyword.text().codePointCount(0, keyword.text().length()))
                .max().orElseThrow()).as("the longest keyword (en-tx Examples)").isEqualTo(53);
    }

    record Keyword(String language, String kind, String text) {
    }

    private static Stream<Keyword> keywords(GherkinDialect dialect) {
        Map<String, List<String>> kinds = new LinkedHashMap<>();
        kinds.put("feature", dialect.getFeatureKeywords());
        kinds.put("rule", dialect.getRuleKeywords());
        kinds.put("background", dialect.getBackgroundKeywords());
        kinds.put("scenario", dialect.getScenarioKeywords());
        kinds.put("scenarioOutline", dialect.getScenarioOutlineKeywords());
        kinds.put("examples", dialect.getExamplesKeywords());
        kinds.put("step", dialect.getStepKeywords());
        return kinds.entrySet().stream().flatMap(kind -> kind.getValue().stream()
                .map(text -> new Keyword(dialect.getLanguage(), kind.getKey(), text.stripTrailing())));
    }
}
