package com.yodawife.easyll.validation;

import com.yodawife.easyll.domain.CsvParseResult;
import com.yodawife.easyll.domain.ScoreDataBundle;
import com.yodawife.easyll.domain.ScoreKey;
import com.yodawife.easyll.domain.UserWordHistory;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

        Path scoreFilePath = Path.of(scoreSource);
        if (!Files.exists(scoreFilePath)) {
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
        Map<ScoreKey, UserWordHistory> histories = new HashMap<>();
        Set<ScoreKey> seenKeys = new HashSet<>();

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

                String userId = record.get(0);
                String pairId = record.get(1);
                String mode = record.get(2);
                String historyRaw = record.get(3);

                if (userId.isBlank()) {
                    errors.add("Score row " + rowNumber + ": USER_ID is empty");
                    continue;
                }
                if (pairId.isBlank()) {
                    errors.add("Score row " + rowNumber + ": PAIR_ID is empty");
                    continue;
                }
                if (mode.isBlank()) {
                    errors.add("Score row " + rowNumber + ": MODE is empty");
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
                // Enforce max 12 — keep last 12 (FIFO means oldest are first)
                if (historyEntries.size() > 12) {
                    historyEntries = historyEntries.subList(historyEntries.size() - 12, historyEntries.size());
                }

                ScoreKey key = new ScoreKey(userId, pairId, mode);
                if (!seenKeys.add(key)) {
                    errors.add("Score row " + rowNumber + ": duplicate key (userId='" + userId
                            + "', pairId='" + pairId + "', mode='" + mode + "')");
                    continue;
                }
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

    private boolean isClasspathSource() {
        return scoreSource.startsWith("classpath:");
    }
}
