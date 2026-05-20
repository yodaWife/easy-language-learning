package com.yodawife.easyll.validation;

import com.yodawife.easyll.domain.CsvParseResult;
import com.yodawife.easyll.domain.ScoreDataBundle;
import com.yodawife.easyll.domain.UserWordKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreCsvParserTest {

    @TempDir
    Path tempDir;

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
        ScoreCsvParser parser = parserFor(tempDir.resolve("nonexistent.csv").toString());
        CsvParseResult<ScoreDataBundle> result = parser.parse();
        assertThat(result).isInstanceOf(CsvParseResult.Success.class);
        ScoreDataBundle bundle = ((CsvParseResult.Success<ScoreDataBundle>) result).value();
        assertThat(bundle.histories()).isEmpty();
    }

    @Test
    void validScoreFileLoadsSuccessfully() throws IOException {
        Path file = tempDir.resolve("scores.csv");
        Files.writeString(file, "alice;Letter;Betű;S,F,S\n");
        ScoreCsvParser parser = parserFor(file.toString());

        CsvParseResult<ScoreDataBundle> result = parser.parse();
        assertThat(result).isInstanceOf(CsvParseResult.Success.class);
        ScoreDataBundle bundle = ((CsvParseResult.Success<ScoreDataBundle>) result).value();
        UserWordKey key = new UserWordKey("alice", "Letter", "Betű");
        assertThat(bundle.histories()).containsKey(key);
        assertThat(bundle.histories().get(key).entries()).containsExactly("S", "F", "S");
    }

    @Test
    void invalidHistorySymbolReturnsFailure() throws IOException {
        Path file = tempDir.resolve("scores.csv");
        Files.writeString(file, "alice;Letter;Betű;S,X,F\n");
        ScoreCsvParser parser = parserFor(file.toString());

        CsvParseResult<ScoreDataBundle> result = parser.parse();
        assertThat(result).isInstanceOf(CsvParseResult.Failure.class);
        List<String> errors = ((CsvParseResult.Failure<ScoreDataBundle>) result).errors();
        assertThat(errors).anyMatch(e -> e.contains("X"));
    }

    @Test
    void emptyHistorySymbolReturnsFailure() throws IOException {
        Path file = tempDir.resolve("scores.csv");
        Files.writeString(file, "alice;Letter;Betű;S,,F\n");
        ScoreCsvParser parser = parserFor(file.toString());

        CsvParseResult<ScoreDataBundle> result = parser.parse();
        assertThat(result).isInstanceOf(CsvParseResult.Failure.class);
        List<String> errors = ((CsvParseResult.Failure<ScoreDataBundle>) result).errors();
        assertThat(errors).anyMatch(e -> e.contains("empty history symbol"));
    }

    @Test
    void blankHistoryReturnsFailure() throws IOException {
        Path file = tempDir.resolve("scores.csv");
        Files.writeString(file, "alice;Letter;Betű;\n");
        ScoreCsvParser parser = parserFor(file.toString());

        CsvParseResult<ScoreDataBundle> result = parser.parse();
        assertThat(result).isInstanceOf(CsvParseResult.Failure.class);
        List<String> errors = ((CsvParseResult.Failure<ScoreDataBundle>) result).errors();
        assertThat(errors).anyMatch(e -> e.contains("HISTORY is empty"));
    }

    @Test
    void wrongColumnCountReturnsFailure() throws IOException {
        Path file = tempDir.resolve("scores.csv");
        Files.writeString(file, "alice;Letter\n");
        ScoreCsvParser parser = parserFor(file.toString());

        CsvParseResult<ScoreDataBundle> result = parser.parse();
        assertThat(result).isInstanceOf(CsvParseResult.Failure.class);
    }
}
