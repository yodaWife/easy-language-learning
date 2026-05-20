package com.yodawife.easyll.validation;

import com.yodawife.easyll.domain.CsvParseResult;
import com.yodawife.easyll.domain.WordDataBundle;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class WordCsvParserTest {

    @TempDir
    @Nullable Path tempDir;

    private Path tempDir() {
        return Objects.requireNonNull(tempDir);
    }

    private WordCsvParser parserFor(Path filePath) {
        return new WordCsvParser(filePath.toString());
    }

    @Test
    void validCsvLoadsSuccessfully() throws IOException {
        Path file = tempDir().resolve("words.csv");
        Files.writeString(file, "ENGLISH;HUNGARIAN;EXAMPLE\nLetter;Betű;\nStone;Kő;\n");

        WordCsvParser wordCsvParser = parserFor(file);
        CsvParseResult<WordDataBundle> result = wordCsvParser.parse();

        assertThat(result).isInstanceOf(CsvParseResult.Success.class);
        WordDataBundle bundle = ((CsvParseResult.Success<WordDataBundle>) result).value();
        assertThat(bundle.metadata().fromLanguageName()).isEqualTo("ENGLISH");
        assertThat(bundle.metadata().toLanguageName()).isEqualTo("HUNGARIAN");
        assertThat(bundle.words()).hasSize(2);
    }

    @Test
    void missingHeaderFails() throws IOException {
        Path file = tempDir().resolve("words-no-header.csv");
        Files.writeString(file, "");

        WordCsvParser parser = parserFor(file);
        CsvParseResult<WordDataBundle> result = parser.parse();

        assertThat(result).isInstanceOf(CsvParseResult.Failure.class);
    }

    @Test
    void wrongColumnCountFails() throws IOException {
        Path file = tempDir().resolve("words-bad-columns.csv");
        Files.writeString(file, "ENGLISH;HUNGARIAN;EXAMPLE\nLetter;Betű\n");

        WordCsvParser parser = parserFor(file);
        CsvParseResult<WordDataBundle> result = parser.parse();

        assertThat(result).isInstanceOf(CsvParseResult.Failure.class);
        assertThat(((CsvParseResult.Failure<WordDataBundle>) result).errors())
                .anyMatch(e -> e.contains("expected 3 columns"));
    }

    @Test
    void duplicatePairFails() throws IOException {
        Path file = tempDir().resolve("words-dup.csv");
        Files.writeString(file, "ENGLISH;HUNGARIAN;EXAMPLE\nLetter;Betű;\nLetter;Betű;\n");

        WordCsvParser parser = parserFor(file);
        CsvParseResult<WordDataBundle> result = parser.parse();

        assertThat(result).isInstanceOf(CsvParseResult.Failure.class);
        assertThat(((CsvParseResult.Failure<WordDataBundle>) result).errors())
                .anyMatch(e -> e.contains("Duplicate word pair"));
    }
}
