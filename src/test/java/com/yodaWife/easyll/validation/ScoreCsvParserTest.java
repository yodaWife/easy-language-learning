package com.yodawife.easyll.validation;

import com.yodawife.easyll.domain.CsvParseResult;
import com.yodawife.easyll.domain.ScoreDataBundle;
import com.yodawife.easyll.domain.ScoreKey;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreCsvParserTest {

    @TempDir
    @Nullable Path tempDir;

    private Path tempDir() {
        return Objects.requireNonNull(tempDir);
    }

    private ScoreCsvParser parserFor(String filePath) {
        return new ScoreCsvParser(filePath, new DefaultResourceLoader());
    }

    @Test
    void classpathSourceLoadsSuccessfully() {
        ScoreCsvParser parser = parserFor("classpath:scores-test.csv");

        CsvParseResult<ScoreDataBundle> result = parser.parse();
        assertThat(result).isInstanceOf(CsvParseResult.Success.class);
        ScoreDataBundle bundle = ((CsvParseResult.Success<ScoreDataBundle>) result).value();
        assertThat(bundle.histories()).isNotEmpty();
    }

    @Test
    void missingFilReturnsEmptySuccess() {
        ScoreCsvParser parser = parserFor(tempDir().resolve("nonexistent.csv").toString());
        CsvParseResult<ScoreDataBundle> result = parser.parse();
        assertThat(result).isInstanceOf(CsvParseResult.Success.class);
        ScoreDataBundle bundle = ((CsvParseResult.Success<ScoreDataBundle>) result).value();
        assertThat(bundle.histories()).isEmpty();
    }

    @Test
    void validScoreFileLoadsSuccessfully() throws IOException {
        Path file = tempDir().resolve("scores.csv");
        Files.writeString(file, "user-1;pair-abc;match;S,F,S\n");
        ScoreCsvParser parser = parserFor(file.toString());

        CsvParseResult<ScoreDataBundle> result = parser.parse();
        assertThat(result).isInstanceOf(CsvParseResult.Success.class);
        ScoreDataBundle bundle = ((CsvParseResult.Success<ScoreDataBundle>) result).value();
        ScoreKey key = new ScoreKey("user-1", "pair-abc", "match");
        assertThat(bundle.histories()).containsKey(key);
        assertThat(Objects.requireNonNull(bundle.histories().get(key)).entries()).containsExactly("S", "F", "S");
    }

    @Test
    void invalidHistorySymbolReturnsFailure() throws IOException {
        Path file = tempDir().resolve("scores.csv");
        Files.writeString(file, "user-1;pair-abc;match;S,X,F\n");
        ScoreCsvParser parser = parserFor(file.toString());

        CsvParseResult<ScoreDataBundle> result = parser.parse();
        assertThat(result).isInstanceOf(CsvParseResult.Failure.class);
        List<String> errors = ((CsvParseResult.Failure<ScoreDataBundle>) result).errors();
        assertThat(errors).anyMatch(e -> e.contains("X"));
    }

    @Test
    void emptyHistorySymbolReturnsFailure() throws IOException {
        Path file = tempDir().resolve("scores.csv");
        Files.writeString(file, "user-1;pair-abc;match;S,,F\n");
        ScoreCsvParser parser = parserFor(file.toString());

        CsvParseResult<ScoreDataBundle> result = parser.parse();
        assertThat(result).isInstanceOf(CsvParseResult.Failure.class);
        List<String> errors = ((CsvParseResult.Failure<ScoreDataBundle>) result).errors();
        assertThat(errors).anyMatch(e -> e.contains("empty history symbol"));
    }

    @Test
    void blankHistoryReturnsFailure() throws IOException {
        Path file = tempDir().resolve("scores.csv");
        Files.writeString(file, "user-1;pair-abc;match;\n");
        ScoreCsvParser parser = parserFor(file.toString());

        CsvParseResult<ScoreDataBundle> result = parser.parse();
        assertThat(result).isInstanceOf(CsvParseResult.Failure.class);
        List<String> errors = ((CsvParseResult.Failure<ScoreDataBundle>) result).errors();
        assertThat(errors).anyMatch(e -> e.contains("HISTORY is empty"));
    }

    @Test
    void wrongColumnCountReturnsFailure() throws IOException {
        Path file = tempDir().resolve("scores.csv");
        Files.writeString(file, "user-1;pair-abc\n");
        ScoreCsvParser parser = parserFor(file.toString());

        CsvParseResult<ScoreDataBundle> result = parser.parse();
        assertThat(result).isInstanceOf(CsvParseResult.Failure.class);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("CSV with a single duplicate key returns Failure naming the duplicate and its row number")
    void singleDuplicateKeyReturnsFailure() throws IOException {
        Path file = tempDir().resolve("scores.csv");
        Files.writeString(file,
                """
                user-1;pair-abc;match;S,F
                user-1;pair-abc;match;S
                """);
        var parser = parserFor(file.toString());

        var result = parser.parse();

        assertThat(result).isInstanceOf(CsvParseResult.Failure.class);
        var errors = ((CsvParseResult.Failure<ScoreDataBundle>) result).errors();
        assertThat(errors).hasSize(1);
        assertThat(errors.getFirst())
                .contains("row 2")
                .contains("user-1")
                .contains("pair-abc")
                .contains("match");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("CSV with multiple duplicate keys reports all of them without aborting early")
    void multipleDuplicateKeysReportsAll() throws IOException {
        Path file = tempDir().resolve("scores.csv");
        Files.writeString(file,
                """
                user-1;pair-abc;match;S
                user-2;pair-xyz;match;F
                user-1;pair-abc;match;F
                user-2;pair-xyz;match;S
                """);
        var parser = parserFor(file.toString());

        var result = parser.parse();

        assertThat(result).isInstanceOf(CsvParseResult.Failure.class);
        var errors = ((CsvParseResult.Failure<ScoreDataBundle>) result).errors();
        assertThat(errors).hasSize(2);
        assertThat(errors).anyMatch(e -> e.contains("row 3") && e.contains("user-1") && e.contains("pair-abc"));
        assertThat(errors).anyMatch(e -> e.contains("row 4") && e.contains("user-2") && e.contains("pair-xyz"));
    }
}
