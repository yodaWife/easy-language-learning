package com.yodawife.easyll.validation;

import com.yodawife.easyll.domain.CsvParseResult;
import com.yodawife.easyll.domain.ModeEligibility;
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
import java.util.stream.Collectors;

/**
 * Parses a mode-eligibility CSV file into a list of {@link ModeEligibility} domain objects.
 *
 * <p>Expected CSV format:
 * <pre>
 *   WORD_ID;MODE;ENABLED
 *   hello;flashcards;true
 * </pre>
 *
 * <p>The first row is treated as a column header and is skipped.
 * A missing file is not treated as an error — it is equivalent to an empty file
 * (all words default to enabled per FR-021).
 *
 * <p>This is a plain Java class and must not be registered as a Spring component;
 * it is instantiated by a higher-level service.
 */
public class ModeEligibilityCsvParser {

    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setDelimiter(';')
            .setTrim(true)
            .setHeader()
            .setSkipHeaderRecord(true)
            .build();

    private static final int EXPECTED_COLUMNS = 3;

    private final Path modeEligibilityFilePath;

    public ModeEligibilityCsvParser(Path modeEligibilityFilePath) {
        this.modeEligibilityFilePath = modeEligibilityFilePath;
    }

    /**
     * Parses the mode-eligibility CSV file, validating referential integrity against
     * the provided list of known word IDs.
     *
     * <p>A missing or empty file returns {@link CsvParseResult.Success} with an empty list.
     *
     * @param knownWordIds the word IDs already parsed from words.csv; used for referential integrity
     * @return {@link CsvParseResult.Success} with all parsed entries, or
     *         {@link CsvParseResult.Failure} containing all collected error messages
     */
    public CsvParseResult<List<ModeEligibility>> parse(List<WordId> knownWordIds) {
        if (!Files.exists(modeEligibilityFilePath)) {
            return new CsvParseResult.Success<>(List.of());
        }

        var knownIds = knownWordIds.stream()
                .map(WordId::value)
                .collect(Collectors.toUnmodifiableSet());

        var errors = new ArrayList<String>();
        var eligibilities = new ArrayList<ModeEligibility>();
        Set<String> seenCompositeKeys = new HashSet<>();

        try (var reader = Files.newBufferedReader(modeEligibilityFilePath, StandardCharsets.UTF_8);
             var csvParser = CSV_FORMAT.parse(reader)) {

            var records = csvParser.getRecords();

            if (records.isEmpty()) {
                return new CsvParseResult.Success<>(List.of());
            }

            for (int i = 0; i < records.size(); i++) {
                var record = records.get(i);
                int rowNumber = i + 2; // header is row 1; first data row is row 2

                if (record.size() != EXPECTED_COLUMNS) {
                    errors.add("Row " + rowNumber + ": expected 3 columns, got " + record.size());
                    continue;
                }

                var wordIdValue = record.get(0);
                var mode = record.get(1);
                var enabledRaw = record.get(2);

                if (wordIdValue.isBlank()) {
                    errors.add("Row " + rowNumber + ": WORD_ID is blank");
                    continue;
                }

                if (!knownIds.contains(wordIdValue)) {
                    errors.add("Row " + rowNumber + ": WORD_ID '" + wordIdValue + "' not found in words.csv");
                    continue;
                }

                if (mode.isBlank()) {
                    errors.add("Row " + rowNumber + ": MODE is blank");
                    continue;
                }

                if (!enabledRaw.equalsIgnoreCase("true") && !enabledRaw.equalsIgnoreCase("false")) {
                    errors.add("Row " + rowNumber + ": ENABLED must be 'true' or 'false', got '" + enabledRaw + "'");
                    continue;
                }

                var compositeKey = wordIdValue + "\u0000" + mode;
                if (!seenCompositeKeys.add(compositeKey)) {
                    errors.add("Duplicate entry for WORD_ID '" + wordIdValue + "' + MODE '" + mode + "' at row " + rowNumber);
                    continue;
                }

                boolean enabled = Boolean.parseBoolean(enabledRaw);
                eligibilities.add(new ModeEligibility(new WordId(wordIdValue), mode, enabled));
            }

        } catch (IOException e) {
            return new CsvParseResult.Failure<>(List.of("Failed to read mode-eligibility CSV: " + e));
        }

        if (!errors.isEmpty()) {
            return new CsvParseResult.Failure<>(errors);
        }

        return new CsvParseResult.Success<>(List.copyOf(eligibilities));
    }
}
