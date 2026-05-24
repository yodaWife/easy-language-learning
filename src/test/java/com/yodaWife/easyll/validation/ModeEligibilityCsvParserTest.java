package com.yodawife.easyll.validation;

import com.yodawife.easyll.domain.CsvParseResult;
import com.yodawife.easyll.domain.ModeEligibility;
import com.yodawife.easyll.domain.WordId;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class ModeEligibilityCsvParserTest {

    @TempDir
    @Nullable Path tempDir;

    private Path tempDir() {
        return Objects.requireNonNull(tempDir);
    }

    private static final List<WordId> KNOWN_WORD_IDS = List.of(
            new WordId("hello"),
            new WordId("apple"),
            new WordId("book")
    );

    @Test
    @DisplayName("Valid CSV parses into correct ModeEligibility list with all fields set")
    void validCsvParsesIntoCorrectModeEligibilityList() throws IOException {
        var file = tempDir().resolve("mode-eligibility.csv");
        Files.writeString(file, """
                WORD_ID;MODE;ENABLED
                hello;flashcards;true
                apple;match;false
                """, StandardCharsets.UTF_8);

        var result = new ModeEligibilityCsvParser(file).parse(KNOWN_WORD_IDS);

        assertThat(result).isInstanceOf(CsvParseResult.Success.class);
        var eligibilities = ((CsvParseResult.Success<List<ModeEligibility>>) result).value();
        assertThat(eligibilities).hasSize(2);
        assertThat(eligibilities.get(0).wordId().value()).isEqualTo("hello");
        assertThat(eligibilities.get(0).mode()).isEqualTo("flashcards");
        assertThat(eligibilities.get(0).enabled()).isTrue();
        assertThat(eligibilities.get(1).wordId().value()).isEqualTo("apple");
        assertThat(eligibilities.get(1).mode()).isEqualTo("match");
        assertThat(eligibilities.get(1).enabled()).isFalse();
    }

    @Test
    @DisplayName("Missing file returns Success with empty list — not a Failure")
    void missingFileReturnsSuccessWithEmptyList() {
        var file = tempDir().resolve("nonexistent.csv");

        var result = new ModeEligibilityCsvParser(file).parse(KNOWN_WORD_IDS);

        assertThat(result).isInstanceOf(CsvParseResult.Success.class);
        assertThat(((CsvParseResult.Success<List<ModeEligibility>>) result).value()).isEmpty();
    }

    @Test
    @DisplayName("Empty file returns Success with empty list")
    void emptyFileReturnsSuccessWithEmptyList() throws IOException {
        var file = tempDir().resolve("mode-eligibility.csv");
        Files.writeString(file, "", StandardCharsets.UTF_8);

        var result = new ModeEligibilityCsvParser(file).parse(KNOWN_WORD_IDS);

        assertThat(result).isInstanceOf(CsvParseResult.Success.class);
        assertThat(((CsvParseResult.Success<List<ModeEligibility>>) result).value()).isEmpty();
    }

    @Test
    @DisplayName("WORD_ID not present in known word list returns Failure")
    void wordIdNotInKnownListReturnsFailure() throws IOException {
        var file = tempDir().resolve("mode-eligibility.csv");
        Files.writeString(file, """
                WORD_ID;MODE;ENABLED
                unknown_word;flashcards;true
                """, StandardCharsets.UTF_8);

        var result = new ModeEligibilityCsvParser(file).parse(KNOWN_WORD_IDS);

        assertThat(result).isInstanceOf(CsvParseResult.Failure.class);
        assertThat(((CsvParseResult.Failure<List<ModeEligibility>>) result).errors())
                .anyMatch(e -> e.contains("unknown_word"));
    }

    @Test
    @DisplayName("Duplicate WORD_ID + MODE combination returns Failure mentioning both values")
    void duplicateWordIdAndModeCombinationReturnsFailure() throws IOException {
        var file = tempDir().resolve("mode-eligibility.csv");
        Files.writeString(file, """
                WORD_ID;MODE;ENABLED
                hello;flashcards;true
                hello;flashcards;false
                """, StandardCharsets.UTF_8);

        var result = new ModeEligibilityCsvParser(file).parse(KNOWN_WORD_IDS);

        assertThat(result).isInstanceOf(CsvParseResult.Failure.class);
        assertThat(((CsvParseResult.Failure<List<ModeEligibility>>) result).errors())
                .anyMatch(e -> e.contains("hello") && e.contains("flashcards"));
    }

    @Test
    @DisplayName("Invalid ENABLED value returns Failure containing the row number")
    void invalidEnabledValueReturnsFailureWithRowNumber() throws IOException {
        var file = tempDir().resolve("mode-eligibility.csv");
        Files.writeString(file, """
                WORD_ID;MODE;ENABLED
                hello;flashcards;maybe
                """, StandardCharsets.UTF_8);

        var result = new ModeEligibilityCsvParser(file).parse(KNOWN_WORD_IDS);

        assertThat(result).isInstanceOf(CsvParseResult.Failure.class);
        assertThat(((CsvParseResult.Failure<List<ModeEligibility>>) result).errors())
                .anyMatch(e -> e.contains("2"));
    }
}
