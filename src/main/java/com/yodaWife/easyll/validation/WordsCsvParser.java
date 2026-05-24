package com.yodawife.easyll.validation;

import com.yodawife.easyll.domain.CsvParseResult;
import com.yodawife.easyll.domain.Word;
import com.yodawife.easyll.domain.WordId;
import org.apache.commons.csv.CSVFormat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses a words.csv file into a list of {@link Word} domain objects.
 *
 * <p>Expected CSV format:
 * <pre>
 *   WORD_ID;FROM;TO;EXAMPLE;GLOBAL_ENABLED
 *   hello;Hallo;hello;Hallo, Welt!;true
 * </pre>
 *
 * <p>The first row is treated as a column header and is skipped.
 * This is a plain Java class and must not be registered as a Spring component;
 * it is instantiated by a higher-level service.
 */
public class WordsCsvParser {

    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setDelimiter(';')
            .setTrim(true)
            .setHeader()
            .setSkipHeaderRecord(true)
            .build();

    private static final int EXPECTED_COLUMNS = 5;

    private final Path wordsFilePath;

    public WordsCsvParser(Path wordsFilePath) {
        this.wordsFilePath = wordsFilePath;
    }

    /**
     * Holds the list of successfully parsed {@link Word} objects.
     *
     * @param words immutable list of words; never null
     */
    public record WordParseData(List<Word> words) {}

    /**
     * Parses the words CSV file.
     *
     * @return {@link CsvParseResult.Success} with a {@link WordParseData}, or
     *         {@link CsvParseResult.Failure} containing all collected error messages
     */
    public CsvParseResult<WordParseData> parse() {
        if (!Files.exists(wordsFilePath)) {
            return new CsvParseResult.Failure<>(List.of("words.csv not found: " + wordsFilePath));
        }

        var errors = new ArrayList<String>();
        var words = new ArrayList<Word>();
        Set<String> seenWordIds = new HashSet<>();

        try (var reader = Files.newBufferedReader(wordsFilePath, StandardCharsets.UTF_8);
             var csvParser = CSV_FORMAT.parse(reader)) {

            var records = csvParser.getRecords();

            if (records.isEmpty()) {
                return new CsvParseResult.Failure<>(List.of("words.csv is empty or has no data rows"));
            }

            for (int i = 0; i < records.size(); i++) {
                var record = records.get(i);
                int rowNumber = i + 2; // header is row 1; first data row is row 2

                if (record.size() != EXPECTED_COLUMNS) {
                    errors.add("Row " + rowNumber + ": expected 5 columns, got " + record.size());
                    continue;
                }

                var wordIdValue = record.get(0);
                var fromWord = record.get(1);
                var toWord = record.get(2);
                var example = record.get(3);
                var globalEnabledRaw = record.get(4);

                if (wordIdValue.isBlank()) {
                    errors.add("Row " + rowNumber + ": WORD_ID is blank");
                    continue;
                }

                if (!seenWordIds.add(wordIdValue)) {
                    errors.add("Duplicate WORD_ID: '" + wordIdValue + "' at row " + rowNumber);
                    continue;
                }

                if (fromWord.isBlank()) {
                    errors.add("Row " + rowNumber + ": FROM word is blank");
                    continue;
                }

                if (toWord.isBlank()) {
                    errors.add("Row " + rowNumber + ": TO word is blank");
                    continue;
                }

                if (!globalEnabledRaw.equalsIgnoreCase("true") && !globalEnabledRaw.equalsIgnoreCase("false")) {
                    errors.add("Row " + rowNumber + ": GLOBAL_ENABLED must be 'true' or 'false', got '" + globalEnabledRaw + "'");
                    continue;
                }

                boolean globalEnabled = Boolean.parseBoolean(globalEnabledRaw);
                words.add(new Word(new WordId(wordIdValue), fromWord, toWord, example, globalEnabled));
            }

        } catch (IOException e) {
            return new CsvParseResult.Failure<>(List.of("Failed to read words.csv: " + e));
        }

        if (!errors.isEmpty()) {
            return new CsvParseResult.Failure<>(errors);
        }

        return new CsvParseResult.Success<>(new WordParseData(List.copyOf(words)));
    }
}
