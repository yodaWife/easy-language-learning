package com.yodawife.easyll.validation;

import com.yodawife.easyll.domain.CsvParseResult;
import com.yodawife.easyll.domain.ScoreDataBundle;
import com.yodawife.easyll.domain.UserWordHistory;
import com.yodawife.easyll.domain.UserWordKey;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ScoreCsvParser {

    private final String scoreSource;
    private final ResourceLoader resourceLoader;

    public ScoreCsvParser(@Value("${app.scores.file-path}") String scoreSource,
                          ResourceLoader resourceLoader) {
        this.scoreSource = scoreSource;
        this.resourceLoader = resourceLoader;
    }

    public CsvParseResult<ScoreDataBundle> parse() {
        if (isClasspathSource()) {
            Resource resource = resourceLoader.getResource(scoreSource);
            if (!resource.exists()) {
                return new CsvParseResult.Success<>(ScoreDataBundle.empty());
            }
            try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                return parseFromReader(reader);
            } catch (IOException e) {
                return new CsvParseResult.Failure<>(List.of("Failed to read score CSV: " + e.getMessage()));
            }
        }

        Path scoreFilePath = getScoreFilePath();
        if (!Files.exists(scoreFilePath)) {
            // Missing file is not an error — return empty history
            return new CsvParseResult.Success<>(ScoreDataBundle.empty());
        }

        try (Reader reader = Files.newBufferedReader(scoreFilePath, StandardCharsets.UTF_8)) {
            return parseFromReader(reader);
        } catch (IOException e) {
            return new CsvParseResult.Failure<>(List.of("Failed to read score CSV: " + e.getMessage()));
        }
    }

    private CsvParseResult<ScoreDataBundle> parseFromReader(Reader reader) {
        List<String> errors = new ArrayList<>();
        Map<UserWordKey, UserWordHistory> histories = new HashMap<>();

        try (CSVParser csvParser = CSVFormat.DEFAULT
                     .builder()
                     .setDelimiter(';')
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            List<CSVRecord> records = csvParser.getRecords();

            for (int i = 0; i < records.size(); i++) {
                CSVRecord record = records.get(i);
                int rowNumber = i + 1;

                if (record.size() != 4) {
                    errors.add("Score row " + rowNumber + ": expected 4 columns, got " + record.size());
                    continue;
                }

                String user = record.get(0);
                String fromWord = record.get(1);
                String toWord = record.get(2);
                String historyRaw = record.get(3);

                if (user.isBlank()) {
                    errors.add("Score row " + rowNumber + ": USER is empty");
                    continue;
                }
                if (fromWord.isBlank()) {
                    errors.add("Score row " + rowNumber + ": FROM word is empty");
                    continue;
                }
                if (toWord.isBlank()) {
                    errors.add("Score row " + rowNumber + ": TO word is empty");
                    continue;
                }
                if (historyRaw.isBlank()) {
                    errors.add("Score row " + rowNumber + ": HISTORY is empty");
                    continue;
                }

                List<String> historyEntries = new ArrayList<>();
                String[] parts = historyRaw.split(",", -1);
                boolean rowHasError = false;
                for (String part : parts) {
                    String entry = part.trim();
                    if (entry.isEmpty()) {
                        errors.add("Score row " + rowNumber + ": invalid empty history symbol");
                        rowHasError = true;
                        break;
                    }
                    if (!entry.equals("S") && !entry.equals("F")) {
                        errors.add("Score row " + rowNumber + ": invalid history symbol '" + entry + "' (expected S or F)");
                        rowHasError = true;
                        break;
                    }
                    historyEntries.add(entry);
                }
                if (rowHasError) {
                    continue;
                }
                // Enforce max 10 — keep last 10 (FIFO means oldest are first)
                if (historyEntries.size() > 10) {
                    historyEntries = historyEntries.subList(historyEntries.size() - 10, historyEntries.size());
                }

                UserWordKey key = new UserWordKey(user, fromWord, toWord);
                histories.put(key, new UserWordHistory(historyEntries));
            }

            if (!errors.isEmpty()) {
                return new CsvParseResult.Failure<>(errors);
            }

            return new CsvParseResult.Success<>(new ScoreDataBundle(histories));
        } catch (IOException e) {
            return new CsvParseResult.Failure<>(List.of("Failed to read score CSV: " + e.getMessage()));
        }
    }

    public Path getScoreFilePath() {
        if (!isClasspathSource()) {
            return Path.of(scoreSource);
        }

        Resource resource = resourceLoader.getResource(scoreSource);
        try {
            return resource.getFile().toPath();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Score CSV configured as classpath resource must resolve to a writable file path: " + scoreSource,
                    e
            );
        }
    }

    private boolean isClasspathSource() {
        return scoreSource.startsWith("classpath:");
    }
}
