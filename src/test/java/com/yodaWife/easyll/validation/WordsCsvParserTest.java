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

class WordsCsvParserTest {

    @TempDir
    @Nullable Path tempDir;

    private Path tempDir() {
        return Objects.requireNonNull(tempDir);
    }

    @Test
    @DisplayName("Valid CSV with 3 rows parses into 3 Word objects with all fields set correctly")
    void validCsvParsesWith3RowsAndAllFieldsCorrect() throws IOException {
        var file = tempDir().resolve("words.csv");
        Files.writeString(file, """
                WORD_ID;FROM;TO;EXAMPLE;GLOBAL_ENABLED
                hello;Hallo;hello;Hallo Welt;true
                apple;Apfel;apple;Ein Apfel;false
                book;Buch;book;Das Buch;true
                """, StandardCharsets.UTF_8);

        var result = new WordsCsvParser(file).parse();

        assertThat(result).isInstanceOf(CsvParseResult.Success.class);
        var data = ((CsvParseResult.Success<WordsCsvParser.WordParseData>) result).value();
        var words = data.words();
        assertThat(words).hasSize(3);

        assertThat(words.get(0).wordId().value()).isEqualTo("hello");
        assertThat(words.get(0).fromWord()).isEqualTo("Hallo");
        assertThat(words.get(0).toWord()).isEqualTo("hello");
        assertThat(words.get(0).example()).isEqualTo("Hallo Welt");
        assertThat(words.get(0).globalEnabled()).isTrue();

        assertThat(words.get(1).wordId().value()).isEqualTo("apple");
        assertThat(words.get(1).fromWord()).isEqualTo("Apfel");
        assertThat(words.get(1).toWord()).isEqualTo("apple");
        assertThat(words.get(1).example()).isEqualTo("Ein Apfel");
        assertThat(words.get(1).globalEnabled()).isFalse();

        assertThat(words.get(2).wordId().value()).isEqualTo("book");
        assertThat(words.get(2).fromWord()).isEqualTo("Buch");
        assertThat(words.get(2).toWord()).isEqualTo("book");
        assertThat(words.get(2).example()).isEqualTo("Das Buch");
        assertThat(words.get(2).globalEnabled()).isTrue();
    }

    @Test
    @DisplayName("GLOBAL_ENABLED 'true' is mapped to boolean true")
    void globalEnabledTrueIsMappedToBooleanTrue() throws IOException {
        var file = tempDir().resolve("words.csv");
        Files.writeString(file, """
                WORD_ID;FROM;TO;EXAMPLE;GLOBAL_ENABLED
                hello;Hallo;hello;;true
                """, StandardCharsets.UTF_8);

        var result = new WordsCsvParser(file).parse();

        var data = ((CsvParseResult.Success<WordsCsvParser.WordParseData>) result).value();
        assertThat(data.words().getFirst().globalEnabled()).isTrue();
    }

    @Test
    @DisplayName("GLOBAL_ENABLED 'false' is mapped to boolean false")
    void globalEnabledFalseIsMappedToBooleanFalse() throws IOException {
        var file = tempDir().resolve("words.csv");
        Files.writeString(file, """
                WORD_ID;FROM;TO;EXAMPLE;GLOBAL_ENABLED
                hello;Hallo;hello;;false
                """, StandardCharsets.UTF_8);

        var result = new WordsCsvParser(file).parse();

        var data = ((CsvParseResult.Success<WordsCsvParser.WordParseData>) result).value();
        assertThat(data.words().getFirst().globalEnabled()).isFalse();
    }
}
