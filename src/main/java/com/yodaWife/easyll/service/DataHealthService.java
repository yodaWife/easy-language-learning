package com.yodawife.easyll.service;

import com.yodawife.easyll.domain.CsvParseResult;
import com.yodawife.easyll.domain.ScoreDataBundle;
import com.yodawife.easyll.domain.WordDataBundle;
import com.yodawife.easyll.validation.ScoreCsvParser;
import com.yodawife.easyll.validation.WordCsvParser;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DataHealthService {

    private static final Logger log = LoggerFactory.getLogger(DataHealthService.class);

    private final WordCsvParser wordCsvParser;
    private final ScoreCsvParser scoreCsvParser;

    private volatile DataSnapshot currentSnapshot = DataSnapshot.degraded(List.of("Data not yet loaded"));

    public DataHealthService(WordCsvParser wordCsvParser, ScoreCsvParser scoreCsvParser) {
        this.wordCsvParser = wordCsvParser;
        this.scoreCsvParser = scoreCsvParser;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    public synchronized void reload() {
        log.info("Loading CSV data...");
        List<String> errors = new ArrayList<>();

        WordDataBundle wordData = null;
        ScoreDataBundle scoreData = null;

        CsvParseResult<WordDataBundle> wordResult = wordCsvParser.parse();
        switch (wordResult) {
            case CsvParseResult.Success<WordDataBundle> s -> wordData = s.value();
            case CsvParseResult.Failure<WordDataBundle> f -> {
                log.warn("Word CSV validation failed: {}", f.errors());
                errors.addAll(f.errors());
            }
        }

        CsvParseResult<ScoreDataBundle> scoreResult = scoreCsvParser.parse();
        switch (scoreResult) {
            case CsvParseResult.Success<ScoreDataBundle> s -> scoreData = s.value();
            case CsvParseResult.Failure<ScoreDataBundle> f -> {
                log.warn("Score CSV validation failed: {}", f.errors());
                errors.addAll(f.errors());
            }
        }

        if (errors.isEmpty() && wordData != null && scoreData != null) {
            currentSnapshot = DataSnapshot.healthy(wordData, scoreData);
            log.info("Data loaded successfully. {} words, {} score entries.",
                    wordData.words().size(), scoreData.histories().size());
        } else {
            currentSnapshot = DataSnapshot.degraded(errors);
            log.warn("Data loading finished with {} error(s).", errors.size());
        }
    }

    public DataSnapshot snapshot() {
        return currentSnapshot;
    }

    public synchronized void reportRuntimeError(String errorMessage) {
        log.error("Runtime data error: {}", errorMessage);
        currentSnapshot = DataSnapshot.degraded(List.of(errorMessage));
    }
}
