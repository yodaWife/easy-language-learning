package com.yodawife.easyll.service;

import com.yodawife.easyll.service.CsvDictionaryParser.ParsedWordRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CsvDictionaryParserTest {

    private static final String VALID_HEADER = "ENGLISH,HUNGARIAN,EXAMPLE";

    private final CsvDictionaryParser parser = new CsvDictionaryParser();

    // ─── Fatal failures ───────────────────────────────────────────────────────

    @Test
    @DisplayName("parse() with empty string returns Failure with 'empty' in message")
    void parseEmptyStringReturnsFailure() {
        var result = parser.parse("");

        assertThat(result).isInstanceOf(CsvDictionaryParser.ParseResult.Failure.class);
        var failure = (CsvDictionaryParser.ParseResult.Failure) result;
        assertThat(failure.errorMessage()).containsIgnoringCase("empty");
    }

    @Test
    @DisplayName("parse() with null returns Failure with 'empty' in message")
    void parseNullReturnsFailure() {
        var result = parser.parse(null);

        assertThat(result).isInstanceOf(CsvDictionaryParser.ParseResult.Failure.class);
        var failure = (CsvDictionaryParser.ParseResult.Failure) result;
        assertThat(failure.errorMessage()).containsIgnoringCase("empty");
    }

    @Test
    @DisplayName("parse() with wrong header (ENGLISH,POLISH,EXAMPLE) returns Failure")
    void parseWrongHeaderReturnsFailure() {
        var csv = """
                ENGLISH,POLISH,EXAMPLE
                sweet,słodki,a sweet taste
                """;

        var result = parser.parse(csv);

        assertThat(result).isInstanceOf(CsvDictionaryParser.ParseResult.Failure.class);
        var failure = (CsvDictionaryParser.ParseResult.Failure) result;
        assertThat(failure.errorMessage()).containsIgnoringCase("header");
    }

    @Test
    @DisplayName("parse() with missing header (first line is data) returns Failure")
    void parseMissingHeaderReturnsFailure() {
        var csv = "sweet,édes,a sweet taste";

        var result = parser.parse(csv);

        assertThat(result).isInstanceOf(CsvDictionaryParser.ParseResult.Failure.class);
        var failure = (CsvDictionaryParser.ParseResult.Failure) result;
        assertThat(failure.errorMessage()).containsIgnoringCase("header");
    }

    // ─── Success: header-only and basic happy path ────────────────────────────

    @Test
    @DisplayName("parse() with valid header and no data rows returns Success with empty validRows")
    void parseHeaderOnlyReturnsSuccessWithEmptyRows() {
        var result = parser.parse(VALID_HEADER);

        assertThat(result).isInstanceOf(CsvDictionaryParser.ParseResult.Success.class);
        var success = (CsvDictionaryParser.ParseResult.Success) result;
        assertThat(success.validRows()).isEmpty();
        assertThat(success.rowErrors()).isEmpty();
        assertThat(success.skippedInFileCount()).isZero();
    }

    @Test
    @DisplayName("parse() with valid header and 3 data rows returns Success with 3 validRows and 0 skipped")
    void parseValidCsvReturnsThreeRows() {
        var csv = """
                ENGLISH,HUNGARIAN,EXAMPLE
                Cat,Macska,The cat sat on the mat
                Dog,Kutya,A good dog
                Bird,Madár,A bird in the sky
                """;

        var result = parser.parse(csv);

        assertThat(result).isInstanceOf(CsvDictionaryParser.ParseResult.Success.class);
        var success = (CsvDictionaryParser.ParseResult.Success) result;
        assertThat(success.validRows()).hasSize(3);
        assertThat(success.skippedInFileCount()).isZero();
        assertThat(success.rowErrors()).isEmpty();
    }

    // ─── Normalization ────────────────────────────────────────────────────────

    @Test
    @DisplayName("parse() capitalizes first letter of ENGLISH and HUNGARIAN when lowercase")
    void parseLowercaseWordsAreCapitalized() {
        var csv = VALID_HEADER + "\nsweet,édes,a sweet taste";

        var result = parser.parse(csv);

        assertThat(result).isInstanceOf(CsvDictionaryParser.ParseResult.Success.class);
        var success = (CsvDictionaryParser.ParseResult.Success) result;
        assertThat(success.validRows()).hasSize(1);
        var row = success.validRows().getFirst();
        assertThat(row.fromWord()).isEqualTo("Sweet");
        assertThat(row.toWord()).isEqualTo("Édes");
    }

    @Test
    @DisplayName("parse() leaves ENGLISH and HUNGARIAN unchanged when already capitalized")
    void parseAlreadyCapitalizedWordsAreUnchanged() {
        var csv = VALID_HEADER + "\nSweet,Édes,a sweet taste";

        var result = parser.parse(csv);

        assertThat(result).isInstanceOf(CsvDictionaryParser.ParseResult.Success.class);
        var success = (CsvDictionaryParser.ParseResult.Success) result;
        var row = success.validRows().getFirst();
        assertThat(row.fromWord()).isEqualTo("Sweet");
        assertThat(row.toWord()).isEqualTo("Édes");
    }

    @Test
    @DisplayName("parse() trims surrounding whitespace and normalizes capitalization")
    void parseWhitespaceAroundWordsIsTrimmedAndNormalized() {
        var csv = VALID_HEADER + "\n  sweet  ,  édes  ,  a sweet taste  ";

        var result = parser.parse(csv);

        assertThat(result).isInstanceOf(CsvDictionaryParser.ParseResult.Success.class);
        var success = (CsvDictionaryParser.ParseResult.Success) result;
        var row = success.validRows().getFirst();
        assertThat(row.fromWord()).isEqualTo("Sweet");
        assertThat(row.toWord()).isEqualTo("Édes");
        assertThat(row.example()).isEqualTo("a sweet taste");
    }

    @Test
    @DisplayName("parse() treats empty EXAMPLE column as empty string in ParsedWordRow")
    void parseEmptyExampleColumnResultsInEmptyString() {
        var csv = VALID_HEADER + "\nsweet,édes,";

        var result = parser.parse(csv);

        assertThat(result).isInstanceOf(CsvDictionaryParser.ParseResult.Success.class);
        var success = (CsvDictionaryParser.ParseResult.Success) result;
        var row = success.validRows().getFirst();
        assertThat(row.example()).isEmpty();
    }

    @Test
    @DisplayName("parse() trims whitespace-only EXAMPLE column to empty string")
    void parseWhitespaceOnlyExampleBecomesEmptyString() {
        var csv = VALID_HEADER + "\nsweet,édes,   ";

        var result = parser.parse(csv);

        assertThat(result).isInstanceOf(CsvDictionaryParser.ParseResult.Success.class);
        var success = (CsvDictionaryParser.ParseResult.Success) result;
        var row = success.validRows().getFirst();
        assertThat(row.example()).isEmpty();
    }

    // ─── Deduplication ───────────────────────────────────────────────────────

    @Test
    @DisplayName("parse() skips second occurrence of identical (FROM,TO) pair and increments skippedInFileCount")
    void parseDuplicatePairIsSkipped() {
        var csv = """
                ENGLISH,HUNGARIAN,EXAMPLE
                Sweet,Édes,first occurrence
                Sweet,Édes,second occurrence
                """;

        var result = parser.parse(csv);

        assertThat(result).isInstanceOf(CsvDictionaryParser.ParseResult.Success.class);
        var success = (CsvDictionaryParser.ParseResult.Success) result;
        assertThat(success.validRows()).hasSize(1);
        assertThat(success.skippedInFileCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("parse() with 3+ rows where only duplicates after first are skipped, validRows correct")
    void parseMultipleDuplicatesOnlyFirstOccurrenceKept() {
        var csv = """
                ENGLISH,HUNGARIAN,EXAMPLE
                Sweet,Édes,first
                Cat,Macska,a cat
                Sweet,Édes,duplicate
                Sweet,Édes,another duplicate
                """;

        var result = parser.parse(csv);

        assertThat(result).isInstanceOf(CsvDictionaryParser.ParseResult.Success.class);
        var success = (CsvDictionaryParser.ParseResult.Success) result;
        assertThat(success.validRows()).hasSize(2);
        assertThat(success.skippedInFileCount()).isEqualTo(2);
        assertThat(success.validRows())
                .extracting(ParsedWordRow::fromWord)
                .containsExactly("Sweet", "Cat");
    }

    @Test
    @DisplayName("parse() treats rows that normalize to the same (FROM,TO) as duplicates")
    void parseRowsNormalizingToSamePairTreatedAsDuplicate() {
        var csv = """
                ENGLISH,HUNGARIAN,EXAMPLE
                sweet,édes,lowercase first
                Sweet,Édes,capitalized second
                """;

        var result = parser.parse(csv);

        assertThat(result).isInstanceOf(CsvDictionaryParser.ParseResult.Success.class);
        var success = (CsvDictionaryParser.ParseResult.Success) result;
        assertThat(success.validRows()).hasSize(1);
        assertThat(success.skippedInFileCount()).isEqualTo(1);
    }

    // ─── Row-level errors ────────────────────────────────────────────────────

    @Test
    @DisplayName("parse() records row-level error for row with only 2 columns; other rows still valid")
    void parseTwoColumnRowRecordsErrorOtherRowsValid() {
        var csv = """
                ENGLISH,HUNGARIAN,EXAMPLE
                sweet,édes
                cat,macska,a cat
                """;

        var result = parser.parse(csv);

        assertThat(result).isInstanceOf(CsvDictionaryParser.ParseResult.Success.class);
        var success = (CsvDictionaryParser.ParseResult.Success) result;
        assertThat(success.rowErrors()).hasSize(1);
        assertThat(success.validRows()).hasSize(1);
        assertThat(success.validRows().getFirst().fromWord()).isEqualTo("Cat");
    }

    @Test
    @DisplayName("parse() records row-level error for row with 4 or more columns")
    void parseFourColumnRowRecordsError() {
        var csv = """
                ENGLISH,HUNGARIAN,EXAMPLE
                sweet,édes,example,extra
                cat,macska,a cat
                """;

        var result = parser.parse(csv);

        assertThat(result).isInstanceOf(CsvDictionaryParser.ParseResult.Success.class);
        var success = (CsvDictionaryParser.ParseResult.Success) result;
        assertThat(success.rowErrors()).hasSize(1);
        assertThat(success.validRows()).hasSize(1);
    }

    @Test
    @DisplayName("parse() records row-level error when ENGLISH column is blank")
    void parseBlankEnglishColumnRecordsError() {
        var csv = VALID_HEADER + "\n  ,édes,example";

        var result = parser.parse(csv);

        assertThat(result).isInstanceOf(CsvDictionaryParser.ParseResult.Success.class);
        var success = (CsvDictionaryParser.ParseResult.Success) result;
        assertThat(success.rowErrors()).hasSize(1);
        assertThat(success.validRows()).isEmpty();
    }

    @Test
    @DisplayName("parse() records row-level error when HUNGARIAN column is blank")
    void parseBlankHungarianColumnRecordsError() {
        var csv = VALID_HEADER + "\nsweet,  ,example";

        var result = parser.parse(csv);

        assertThat(result).isInstanceOf(CsvDictionaryParser.ParseResult.Success.class);
        var success = (CsvDictionaryParser.ParseResult.Success) result;
        assertThat(success.rowErrors()).hasSize(1);
        assertThat(success.validRows()).isEmpty();
    }

    // ─── Mixed and edge cases ─────────────────────────────────────────────────

    @Test
    @DisplayName("parse() silently skips empty lines between data rows")
    void parseEmptyLinesBetweenDataRowsAreIgnored() {
        var csv = """
                ENGLISH,HUNGARIAN,EXAMPLE

                sweet,édes,a sweet taste

                cat,macska,a cat

                """;

        var result = parser.parse(csv);

        assertThat(result).isInstanceOf(CsvDictionaryParser.ParseResult.Success.class);
        var success = (CsvDictionaryParser.ParseResult.Success) result;
        assertThat(success.validRows()).hasSize(2);
        assertThat(success.rowErrors()).isEmpty();
    }

    @Test
    @DisplayName("parse() separates invalid rows into rowErrors while keeping valid rows in validRows")
    void parseMixedRowsSeparatesValidAndInvalid() {
        var csv = """
                ENGLISH,HUNGARIAN,EXAMPLE
                sweet,édes,a sweet taste
                ,macska,missing english
                cat,macska,a cat
                tooMany,columns,here,extra
                bird,madár,a bird
                """;

        var result = parser.parse(csv);

        assertThat(result).isInstanceOf(CsvDictionaryParser.ParseResult.Success.class);
        var success = (CsvDictionaryParser.ParseResult.Success) result;
        assertThat(success.validRows()).hasSize(3);
        assertThat(success.rowErrors()).hasSize(2);
    }

    @Test
    @DisplayName("parse() returns validRows as an unmodifiable list")
    void parseValidRowsAreImmutable() {
        var csv = VALID_HEADER + "\nsweet,édes,example";

        var result = parser.parse(csv);

        assertThat(result).isInstanceOf(CsvDictionaryParser.ParseResult.Success.class);
        var success = (CsvDictionaryParser.ParseResult.Success) result;
        assertThat(success.validRows())
                .isUnmodifiable();
    }

    // ─── Parameterized: invalid header variants ───────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "ENGLISH,HUNGARIAN",
            "english,hungarian,example",
            "HUNGARIAN,ENGLISH,EXAMPLE",
            "ENGLISH,HUNGARIAN,EXAMPLE,EXTRA"
    })
    @DisplayName("parse() returns Failure for any header that does not match exactly ENGLISH,HUNGARIAN,EXAMPLE")
    void parseInvalidHeaderVariantsReturnFailure(String invalidHeader) {
        var csv = invalidHeader + "\nsweet,édes,example";

        var result = parser.parse(csv);

        assertThat(result).isInstanceOf(CsvDictionaryParser.ParseResult.Failure.class);
    }
}
