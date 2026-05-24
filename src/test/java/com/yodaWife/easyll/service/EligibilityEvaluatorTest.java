package com.yodawife.easyll.service;

import com.yodawife.easyll.domain.ModeEligibility;
import com.yodawife.easyll.domain.Word;
import com.yodawife.easyll.domain.WordId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EligibilityEvaluatorTest {

    private final EligibilityEvaluator evaluator = new EligibilityEvaluator();

    private static final String MODE = "flashcards";

    private Word word(String id, boolean globalEnabled) {
        return new Word(new WordId(id), "From", "To", "", globalEnabled);
    }

    private ModeEligibility eligibility(String wordId, String mode, boolean enabled) {
        return new ModeEligibility(new WordId(wordId), mode, enabled);
    }

    @Test
    @DisplayName("globalEnabled=true and mode entry enabled=true → word is eligible")
    void globalEnabledTrueAndModeEntryEnabledTrueResultsInEligible() {
        var w = word("hello", true);
        var entries = List.of(eligibility("hello", MODE, true));

        assertThat(evaluator.isEligible(w, MODE, entries)).isTrue();
    }

    @Test
    @DisplayName("globalEnabled=false and mode entry enabled=true → word is not eligible")
    void globalEnabledFalseOverridesModeLevelEnabled() {
        var w = word("hello", false);
        var entries = List.of(eligibility("hello", MODE, true));

        assertThat(evaluator.isEligible(w, MODE, entries)).isFalse();
    }

    @Test
    @DisplayName("globalEnabled=true and mode entry enabled=false → word is not eligible")
    void modeEntryEnabledFalseResultsInIneligible() {
        var w = word("hello", true);
        var entries = List.of(eligibility("hello", MODE, false));

        assertThat(evaluator.isEligible(w, MODE, entries)).isFalse();
    }

    @Test
    @DisplayName("globalEnabled=true with no mode entry → word is eligible by default (FR-021)")
    void globalEnabledTrueWithNoModeEntryIsEligibleByDefault() {
        var w = word("hello", true);

        assertThat(evaluator.isEligible(w, MODE, List.of())).isTrue();
    }

    @Test
    @DisplayName("filterEligible returns only words that are eligible for the given mode")
    void filterEligibleReturnsOnlyEligibleWords() {
        var eligible = word("hello", true);
        var disabledGlobally = word("apple", false);
        var disabledByMode = word("book", true);
        var entries = List.of(eligibility("book", MODE, false));

        var result = evaluator.filterEligible(List.of(eligible, disabledGlobally, disabledByMode), MODE, entries);

        assertThat(result)
                .hasSize(1)
                .containsExactly(eligible);
    }

    @Test
    @DisplayName("filterEligible with empty word list returns empty list")
    void filterEligibleWithEmptyWordListReturnsEmptyList() {
        var result = evaluator.filterEligible(List.of(), MODE, List.of());

        assertThat(result).isEmpty();
    }
}
