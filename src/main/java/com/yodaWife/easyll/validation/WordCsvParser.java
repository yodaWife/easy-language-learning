package com.yodawife.easyll.validation;

import com.yodawife.easyll.domain.CsvParseResult;
import com.yodawife.easyll.domain.LanguageMetadata;
import com.yodawife.easyll.domain.WordDataBundle;
import com.yodawife.easyll.domain.WordEntry;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @deprecated Use {@link com.yodawife.easyll.validation.WordsCsvParser} for multi-language support.
 */
@Deprecated
@Component
public class WordCsvParser {

    private final String source;

    public WordCsvParser(@Value("${app.words.source:classpath:data/dictionary.csv}") String source) {
        this.source = source;
    }

    public CsvParseResult<WordDataBundle> parse() {
        List<String> errors = new ArrayList<>();

        try (Reader reader = openReader();
             CSVParser csvParser = CSVFormat.DEFAULT
                     .builder()
                     .setDelimiter(';')
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            List<CSVRecord> records = csvParser.getRecords();

            if (records.isEmpty()) {
                return new CsvParseResult.Failure<>(List.of("Word CSV is empty or missing header row"));
            }

            // First row is the header / language metadata
            CSVRecord headerRecord = records.getFirst();
            if (headerRecord.size() != 3) {
                return new CsvParseResult.Failure<>(List.of("Word CSV header row must have exactly 3 columns (FROM_LANG;TO_LANG;EXAMPLE)"));
            }
            String fromLang = headerRecord.get(0).trim();
            String toLang = headerRecord.get(1).trim();
            if (fromLang.isBlank()) {
                errors.add("Header row: FROM language name is empty");
            }
            if (toLang.isBlank()) {
                errors.add("Header row: TO language name is empty");
            }
            if (!errors.isEmpty()) {
                return new CsvParseResult.Failure<>(errors);
            }
            LanguageMetadata metadata = new LanguageMetadata(fromLang, toLang);

            // Parse data rows
            List<WordEntry> entries = new ArrayList<>();
            Set<String> seenPairs = new HashSet<>();

            for (int i = 1; i < records.size(); i++) {
                CSVRecord record = records.get(i);
                int rowNumber = i + 1; // 1-based for user-facing messages

                if (record.size() != 3) {
                    errors.add("Row " + rowNumber + ": expected 3 columns, got " + record.size());
                    continue;
                }

                String fromWord = record.get(0).trim();
                String toWord = record.get(1).trim();
                String example = record.get(2).trim();

                if (fromWord.isBlank()) {
                    errors.add("Row " + rowNumber + ": FROM word is empty");
                    continue;
                }
                if (toWord.isBlank()) {
                    errors.add("Row " + rowNumber + ": TO word is empty");
                    continue;
                }

                String pairKey = fromWord + "\u0000" + toWord;
                if (!seenPairs.add(pairKey)) {
                    errors.add("Duplicate word pair: '" + fromWord + "' / '" + toWord + "'");
                    continue;
                }

                entries.add(new WordEntry(fromWord, toWord, example));
            }

            if (!errors.isEmpty()) {
                return new CsvParseResult.Failure<>(errors);
            }

            return new CsvParseResult.Success<>(new WordDataBundle(metadata, entries));

        } catch (IOException e) {
            return new CsvParseResult.Failure<>(List.of("Failed to read word CSV: " + e.getMessage()));
        }
    }

    private Reader openReader() throws IOException {
        if (source.startsWith("classpath:")) {
            String classpathPath = source.substring("classpath:".length());
            var resource = new ClassPathResource(classpathPath);
            return new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
        }
        return Files.newBufferedReader(Path.of(source), StandardCharsets.UTF_8);
    }
}
