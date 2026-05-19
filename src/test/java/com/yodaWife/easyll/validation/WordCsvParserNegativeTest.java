package com.yodawife.easyll.validation;

import com.yodawife.easyll.domain.CsvParseResult;
import com.yodawife.easyll.domain.WordDataBundle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WordCsvParserNegativeTest {

    @TempDir
    Path tempDir;

    private WordCsvParser parserFor(Path filePath) {
        return new WordCsvParser(filePath.toString());
    }

    @Test
    @DisplayName("Malformed row fails parsing")
    void malformedRowFailsParsing() throws IOException {
        Path file = tempDir.resolve("malformed.csv");
        Files.writeString(file, "ENGLISH;HUNGARIAN;EXAMPLE\nBrokenOnlyOneColumn\n");

        CsvParseResult<WordDataBundle> result = parserFor(file).parse();
        assertThat(result).isInstanceOf(CsvParseResult.Failure.class);
    }

    @Test
    @DisplayName("Blank FROM value fails parsing")
    void blankFromValueFailsParsing() throws IOException {
        Path file = tempDir.resolve("blank-from.csv");
        Files.writeString(file, "ENGLISH;HUNGARIAN;EXAMPLE\n ;Betű;\n");

        CsvParseResult<WordDataBundle> result = parserFor(file).parse();
        assertThat(result).isInstanceOf(CsvParseResult.Failure.class);
        assertThat(((CsvParseResult.Failure<WordDataBundle>) result).errors())
                .anyMatch(e -> e.contains("FROM word is empty"));
    }

    @Test
    @DisplayName("Blank TO value fails parsing")
    void blankToValueFailsParsing() throws IOException {
        Path file = tempDir.resolve("blank-to.csv");
        Files.writeString(file, "ENGLISH;HUNGARIAN;EXAMPLE\nLetter; ;\n");

        CsvParseResult<WordDataBundle> result = parserFor(file).parse();
        assertThat(result).isInstanceOf(CsvParseResult.Failure.class);
        assertThat(((CsvParseResult.Failure<WordDataBundle>) result).errors())
                .anyMatch(e -> e.contains("TO word is empty"));
    }
}
