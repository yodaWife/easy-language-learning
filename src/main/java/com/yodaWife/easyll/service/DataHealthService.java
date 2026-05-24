package com.yodawife.easyll.service;

import com.yodawife.easyll.domain.CsvParseResult;
import com.yodawife.easyll.domain.LanguageBundle;
import com.yodawife.easyll.domain.MultiLanguageDataBundle;
import com.yodawife.easyll.domain.ScoreDataBundle;
import com.yodawife.easyll.validation.MultiLanguageDictionaryParser;
import com.yodawife.easyll.validation.ScoreCsvParser;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DataHealthService {

    private static final Logger log = LoggerFactory.getLogger(DataHealthService.class);

    private final ScoreCsvParser scoreCsvParser;
    private final ApplicationEventPublisher eventPublisher;
    private final MultiLanguageDictionaryParser multiLanguageDictionaryParser;

    private volatile DataSnapshot currentSnapshot = DataSnapshot.degraded(List.of("Data not yet loaded"));

    public DataHealthService(ScoreCsvParser scoreCsvParser,
                             ApplicationEventPublisher eventPublisher,
                             MultiLanguageDictionaryParser multiLanguageDictionaryParser) {
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
        var scoreErrors = new java.util.ArrayList<String>();
        ScoreDataBundle scoreData = null;

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
    var wordsAreHealthy = multiLanguageHealthy;
    var wordErrors = collectWordErrors(multiLanguageData);

        currentSnapshot = new DataSnapshot(
                wordsAreHealthy, scoreData != null,
                wordErrors, scoreErrors,
        null, scoreData, multiLanguageData);

        if (wordsAreHealthy) {
            eventPublisher.publishEvent(new DataReloadedEvent(this));
        var validLanguages = multiLanguageData.bundles().values().stream()
            .filter(LanguageBundle::isValid)
            .count();
        if (scoreData != null) {
        log.info("Data loaded successfully. {} valid language(s), {} score entries.",
            validLanguages, scoreData.histories().size());
            } else {
        log.warn("Score data failed ({} error(s)); multi-language data loaded ({} valid language(s)). Gameplay available.",
            scoreErrors.size(), validLanguages);
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

    private List<String> collectWordErrors(MultiLanguageDataBundle multiLanguageData) {
        return multiLanguageData.bundles().entrySet().stream()
                .flatMap(entry -> entry.getValue().validationErrors().stream())
                .toList();
    }
}
