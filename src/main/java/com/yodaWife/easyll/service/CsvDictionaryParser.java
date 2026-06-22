package com.yodawife.easyll.service;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses, validates, normalizes, and deduplicates a CSV string representing dictionary words.
 *
 * <p>Expected CSV format:
 * <pre>
 *   ENGLISH,HUNGARIAN,EXAMPLE
 *   apple,alma,I eat an apple every day.
 *   ...
 * </pre>
 *
 * <p>This service has no database dependency — all processing is pure in-memory logic.
 */
@Service
public class CsvDictionaryParser {

    private static final Logger log = LoggerFactory.getLogger(CsvDictionaryParser.class);

    private static final String REQUIRED_HEADER = "ENGLISH,HUNGARIAN,EXAMPLE";
    private static final int EXPECTED_COLUMN_COUNT = 3;

    /**
     * Parses the given CSV string and returns a {@link ParseResult} indicating success or failure.
     *
     * @param csv the raw CSV content as a string (already read from multipart upload by the caller)
     * @return {@link ParseResult.Success} with parsed rows and any row-level errors,
     *         or {@link ParseResult.Failure} if the file is empty or the header is invalid
     */
    public ParseResult parse(@Nullable String csv) {
        if (csv == null || csv.isBlank()) {
            log.debug("CSV input is empty or null");
            return new ParseResult.Failure("CSV file is empty");
        }

        var lines = csv.lines().toList();
        if (lines.isEmpty()) {
            return new ParseResult.Failure("CSV file is empty");
        }

        var header = lines.getFirst();
        if (!REQUIRED_HEADER.equals(header)) {
            log.debug("Invalid CSV header: '{}'", header);
            return new ParseResult.Failure("Invalid CSV header: expected '" + REQUIRED_HEADER + "' but got '" + header + "'");
        }

        var dataLines = lines.subList(1, lines.size());
        if (dataLines.isEmpty()) {
            log.debug("CSV contains header only, no data rows");
            return new ParseResult.Success(List.of(), 0, List.of());
        }

        var validRows = new ArrayList<ParsedWordRow>();
        var rowErrors = new ArrayList<String>();
        var seenPairs = new HashSet<String>();
        var skippedInFileCount = 0;

        for (int i = 0; i < dataLines.size(); i++) {
            var lineNumber = i + 2; // 1-based, account for header being line 1
            var line = dataLines.get(i);

            if (line.isBlank()) {
                continue;
            }

            var columns = line.split(",", -1);
            if (columns.length != EXPECTED_COLUMN_COUNT) {
                var reason = "Expected " + EXPECTED_COLUMN_COUNT + " columns but found " + columns.length;
                rowErrors.add("Line " + lineNumber + ": " + reason);
                log.debug("Row validation error at line {}: {}", lineNumber, reason);
                continue;
            }

            var rawEnglish = columns[0].trim();
            var rawHungarian = columns[1].trim();
            var example = columns[2].trim();

            if (rawEnglish.isBlank()) {
                var reason = "ENGLISH column is blank";
                rowErrors.add("Line " + lineNumber + ": " + reason);
                log.debug("Row validation error at line {}: {}", lineNumber, reason);
                continue;
            }
            if (rawHungarian.isBlank()) {
                var reason = "HUNGARIAN column is blank";
                rowErrors.add("Line " + lineNumber + ": " + reason);
                log.debug("Row validation error at line {}: {}", lineNumber, reason);
                continue;
            }

            var fromWord = capitalizeFirst(rawEnglish);
            var toWord = capitalizeFirst(rawHungarian);

            var deduplicationKey = fromWord + "\u0000" + toWord;
            if (!seenPairs.add(deduplicationKey)) {
                skippedInFileCount++;
                log.debug("Duplicate in upload at line {}: ({}, {})", lineNumber, fromWord, toWord);
                continue;
            }

            validRows.add(new ParsedWordRow(fromWord, toWord, example));
        }

        log.debug("CSV parse complete: {} valid rows, {} skipped duplicates, {} row errors",
                validRows.size(), skippedInFileCount, rowErrors.size());

        return new ParseResult.Success(
                List.copyOf(validRows),
                skippedInFileCount,
                List.copyOf(rowErrors)
        );
    }

    private static String capitalizeFirst(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    // -------------------------------------------------------------------------
    // Result types
    // -------------------------------------------------------------------------

    /**
     * The outcome of a CSV parse operation.
     *
     * <p>On a fatal structural error (empty file, invalid header) a {@link Failure} is returned.
     * Otherwise a {@link Success} is returned, which may still carry row-level errors.
     */
    public sealed interface ParseResult {

        /**
         * Returned when the file structure is valid (header OK, at least parseable).
         *
         * @param validRows         normalized, deduplicated rows ready for persistence
         * @param skippedInFileCount number of rows skipped because they duplicated an earlier row in the same file
         * @param rowErrors         per-row validation error messages (non-fatal; other rows are still imported)
         */
        record Success(
                List<ParsedWordRow> validRows,
                int skippedInFileCount,
                List<String> rowErrors
        ) implements ParseResult {}

        /**
         * Returned when the entire file is rejected due to a fatal structural problem.
         *
         * @param errorMessage human-readable description of the fatal error
         */
        record Failure(String errorMessage) implements ParseResult {}
    }

    /**
     * A single normalized dictionary row extracted from the CSV.
     *
     * @param fromWord the source-language word (trimmed, first letter capitalized)
     * @param toWord   the target-language word (trimmed, first letter capitalized)
     * @param example  the optional usage example (trimmed only; may be empty, never null)
     */
    public record ParsedWordRow(String fromWord, String toWord, String example) {

        public ParsedWordRow {
            if (fromWord == null || fromWord.isBlank()) {
                throw new IllegalArgumentException("fromWord must not be blank");
            }
            if (toWord == null || toWord.isBlank()) {
                throw new IllegalArgumentException("toWord must not be blank");
            }
            example = (example == null) ? "" : example;
        }
    }
}
