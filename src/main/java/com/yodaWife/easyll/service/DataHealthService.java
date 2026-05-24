package com.yodawife.easyll.service;

import com.yodawife.easyll.domain.CsvParseResult;
import com.yodawife.easyll.domain.LanguageBundle;
import com.yodawife.easyll.domain.MultiLanguageDataBundle;
import com.yodawife.easyll.domain.ScoreDataBundle;
import com.yodawife.easyll.domain.WordDataBundle;
import com.yodawife.easyll.validation.MultiLanguageDictionaryParser;
import com.yodawife.easyll.validation.ScoreCsvParser;
import com.yodawife.easyll.validation.WordCsvParser;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DataHealthService {

    private static final Logger log = LoggerFactory.getLogger(DataHealthService.class);

    private final WordCsvParser wordCsvParser;
    private final ScoreCsvParser scoreCsvParser;
    private final ApplicationEventPublisher eventPublisher;
    private final MultiLanguageDictionaryParser multiLanguageDictionaryParser;

    private volatile DataSnapshot currentSnapshot = DataSnapshot.degraded(List.of("Data not yet loaded"));

    public DataHealthService(WordCsvParser wordCsvParser, ScoreCsvParser scoreCsvParser,
                             ApplicationEventPublisher eventPublisher,
                             MultiLanguageDictionaryParser multiLanguageDictionaryParser) {
        this.wordCsvParser = wordCsvParser;
        this.scoreCsvParser = scoreCsvParser;
        this.eventPublisher = eventPublisher;
        this.multiLanguageDictionaryParser = multiLanguageDictionaryParser;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    public synchronized void reload() {
        log.info("Loading CSV data...");
        List<String> wordErrors = new ArrayList<>();
        List<String> scoreErrors = new ArrayList<>();

        WordDataBundle wordData = null;
        ScoreDataBundle scoreData = null;

        CsvParseResult<WordDataBundle> wordResult = wordCsvParser.parse();
        switch (wordResult) {
            case CsvParseResult.Success<WordDataBundle> s -> wordData = s.value();
            case CsvParseResult.Failure<WordDataBundle> f -> {
                log.warn("Word CSV validation failed: {}", f.errors());
                wordErrors.addAll(f.errors());
            }
        }

        CsvParseResult<ScoreDataBundle> scoreResult = scoreCsvParser.parse();
        switch (scoreResult) {
            case CsvParseResult.Success<ScoreDataBundle> s -> scoreData = s.value();
            case CsvParseResult.Failure<ScoreDataBundle> f -> {
                log.warn("Score CSV validation failed: {}", f.errors());
                scoreErrors.addAll(f.errors());
            }
        }

        MultiLanguageDataBundle multiLanguageData = multiLanguageDictionaryParser.parseAll();

        boolean multiLanguageHealthy = multiLanguageData.bundles().values().stream()
                .anyMatch(LanguageBundle::isValid);
        boolean wordsAreHealthy = wordData != null || multiLanguageHealthy;

        currentSnapshot = new DataSnapshot(
                wordsAreHealthy, scoreData != null,
                wordErrors, scoreErrors,
                wordData, scoreData, multiLanguageData);

        if (wordsAreHealthy) {
            eventPublisher.publishEvent(new DataReloadedEvent(this));
            if (wordData != null) {
                if (scoreData != null) {
                    log.info("Data loaded successfully. {} words, {} score entries.",
                            wordData.words().size(), scoreData.histories().size());
                } else {
                    log.warn("Score data failed ({} error(s)); word data loaded ({} words). Gameplay available.",
                            scoreErrors.size(), wordData.words().size());
                }
            } else {
                log.info("Multi-language data loaded ({} language(s)). Gameplay available.",
                        multiLanguageData.bundles().size());
            }
        } else {
            log.warn("Data loading failed: {} word error(s), {} score error(s).",
                    wordErrors.size(), scoreErrors.size());
        }
    }

    public DataSnapshot snapshot() {
        return currentSnapshot;
    }

    /**
     * Returns the language codes of all valid language bundles in the current snapshot.
     *
     * @return list of valid language codes, or an empty list if no multi-language data is available
     */
    public List<String> availableLanguages() {
        var multiLanguageData = currentSnapshot.multiLanguageData();
        if (multiLanguageData == null) {
            return List.of();
        }
        return multiLanguageData.bundles().entrySet().stream()
                .filter(entry -> entry.getValue().isValid())
                .map(Map.Entry::getKey)
                .toList();
    }

    public synchronized void reportRuntimeError(String errorMessage) {
        log.error("Runtime data error: {}", errorMessage);
        currentSnapshot = DataSnapshot.degraded(List.of(errorMessage));
    }

    public synchronized void reportScoreWritePathError(String message) {
        log.error("Score write path error: {}", message);
        var current = currentSnapshot;
        currentSnapshot = new DataSnapshot(current.wordsHealthy(), false, current.wordErrors(), List.of(message), current.wordData(), null, current.multiLanguageData());
    }
}
