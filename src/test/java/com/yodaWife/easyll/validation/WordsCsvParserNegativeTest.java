package com.yodawife.easyll.validation;

import com.yodawife.easyll.domain.CsvParseResult;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class WordsCsvParserNegativeTest {

    @TempDir
    @Nullable Path tempDir;

    private Path tempDir() {
        return Objects.requireNonNull(tempDir);
    }

    @Test
    @DisplayName("Non-existent file returns Failure containing 'not found'")
    void nonExistentFileReturnsFailureWithNotFoundMessage() {
        var file = tempDir().resolve("missing.csv");

        var result = new WordsCsvParser(file).parse();

        assertThat(result).isInstanceOf(CsvParseResult.Failure.class);
        assertThat(((CsvParseResult.Failure<WordsCsvParser.WordParseData>) result).errors())
                .anyMatch(e -> e.contains("not found"));
    }

    @Test
    @DisplayName("Empty file returns Failure with 'no data rows' message")
    void emptyFileReturnsFailureWithNoDataRowsMessage() throws IOException {
        var file = tempDir().resolve("words.csv");
        Files.writeString(file, "", StandardCharsets.UTF_8);

        var result = new WordsCsvParser(file).parse();

        assertThat(result).isInstanceOf(CsvParseResult.Failure.class);
        assertThat(((CsvParseResult.Failure<WordsCsvParser.WordParseData>) result).errors())
                .anyMatch(e -> e.contains("no data rows"));
    }

    @Test
    @DisplayName("Row with 4 columns instead of 5 returns Failure containing the row number")
    void rowWith4ColumnsReturnsFailureWithRowNumber() throws IOException {
        var file = tempDir().resolve("words.csv");
        Files.writeString(file, """
                WORD_ID;FROM;TO;EXAMPLE;GLOBAL_ENABLED
                hello;Hallo;hello;true
                """, StandardCharsets.UTF_8);

        var result = new WordsCsvParser(file).parse();

        assertThat(result).isInstanceOf(CsvParseResult.Failure.class);
        assertThat(((CsvParseResult.Failure<WordsCsvParser.WordParseData>) result).errors())
                .anyMatch(e -> e.contains("2"));
    }

    @Test
    @DisplayName("Blank WORD_ID returns Failure containing the row number")
    void blankWordIdReturnsFailureWithRowNumber() throws IOException {
        var file = tempDir().resolve("words.csv");
        Files.writeString(file, """
                WORD_ID;FROM;TO;EXAMPLE;GLOBAL_ENABLED
                 ;Hallo;hello;Hallo Welt;true
                """, StandardCharsets.UTF_8);

        var result = new WordsCsvParser(file).parse();

        assertThat(result).isInstanceOf(CsvParseResult.Failure.class);
        assertThat(((CsvParseResult.Failure<WordsCsvParser.WordParseData>) result).errors())
                .anyMatch(e -> e.contains("2"));
    }

    @Test
    @DisplayName("Duplicate WORD_ID returns Failure mentioning the duplicate value")
    void duplicateWordIdReturnsFailureMentioningDuplicate() throws IOException {
        var file = tempDir().resolve("words.csv");
        Files.writeString(file, """
                WORD_ID;FROM;TO;EXAMPLE;GLOBAL_ENABLED
                hello;Hallo;hello;;true
                hello;Hallo;hello;;true
                """, StandardCharsets.UTF_8);

        var result = new WordsCsvParser(file).parse();

        assertThat(result).isInstanceOf(CsvParseResult.Failure.class);
        assertThat(((CsvParseResult.Failure<WordsCsvParser.WordParseData>) result).errors())
                .anyMatch(e -> e.contains("hello"));
    }

    @Test
    @DisplayName("Invalid GLOBAL_ENABLED value 'maybe' returns Failure containing the row number")
    void invalidGlobalEnabledValueReturnsFailureWithRowNumber() throws IOException {
        var file = tempDir().resolve("words.csv");
        Files.writeString(file, """
                WORD_ID;FROM;TO;EXAMPLE;GLOBAL_ENABLED
                hello;Hallo;hello;;maybe
                """, StandardCharsets.UTF_8);

        var result = new WordsCsvParser(file).parse();

        assertThat(result).isInstanceOf(CsvParseResult.Failure.class);
        assertThat(((CsvParseResult.Failure<WordsCsvParser.WordParseData>) result).errors())
                .anyMatch(e -> e.contains("2"));
    }

    @Test
    @DisplayName("UTF-8 special characters (Polish ą, ę) are parsed correctly without error")
    void utf8SpecialCharactersAreParsedCorrectlyWithoutError() throws IOException {
        var file = tempDir().resolve("words.csv");
        Files.writeString(file, """
                WORD_ID;FROM;TO;EXAMPLE;GLOBAL_ENABLED
                kot;Katze;kot;Małe ąę;true
                """, StandardCharsets.UTF_8);

        var result = new WordsCsvParser(file).parse();

        assertThat(result).isInstanceOf(CsvParseResult.Success.class);
        var data = ((CsvParseResult.Success<WordsCsvParser.WordParseData>) result).value();
        assertThat(data.words()).hasSize(1);
        assertThat(data.words().getFirst().example()).isEqualTo("Małe ąę");
    }
}
