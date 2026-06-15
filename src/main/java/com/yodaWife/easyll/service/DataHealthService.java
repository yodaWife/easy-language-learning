package com.yodawife.easyll.service;

import com.yodawife.easyll.domain.LanguageBundle;
import com.yodawife.easyll.domain.MultiLanguageDataBundle;
import com.yodawife.easyll.repository.DictionaryRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Service
public class DataHealthService {

    private static final Logger log = LoggerFactory.getLogger(DataHealthService.class);

    private final DictionaryRepository dictionaryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final JdbcTemplate jdbcTemplate;

    private volatile DataSnapshot currentSnapshot = DataSnapshot.degraded(List.of("Data not yet loaded"));

    public DataHealthService(DictionaryRepository dictionaryRepository,
                             ApplicationEventPublisher eventPublisher,
                             JdbcTemplate jdbcTemplate) {
        this.dictionaryRepository = dictionaryRepository;
        this.eventPublisher = eventPublisher;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    public synchronized void reload() {
        log.info("Loading data...");
        try {
            var languageCodes = dictionaryRepository.availableLanguages();
            var bundles = new LinkedHashMap<String, LanguageBundle>();
            for (var code : languageCodes) {
                dictionaryRepository.findLanguage(code).ifPresent(bundle -> bundles.put(code, bundle));
            }

            var wordsAreHealthy = !bundles.isEmpty() && bundles.values().stream().anyMatch(LanguageBundle::isValid);
            var wordErrors = bundles.values().stream()
                    .flatMap(bundle -> bundle.validationErrors().stream())
                    .toList();

            var primaryLanguageCode = languageCodes.isEmpty() ? "pl" : languageCodes.getFirst();
            var multiLanguageData = new MultiLanguageDataBundle(bundles, primaryLanguageCode);

            var scoreErrors = new ArrayList<String>();
            var scoresHealthy = false;
            try {
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM score_attempt", Integer.class);
                scoresHealthy = true;
            } catch (Exception e) {
                log.warn("Score health check failed: {}", e.getMessage());
                scoreErrors.add("Score health check failed: " + e.getMessage());
            }

            currentSnapshot = new DataSnapshot(wordsAreHealthy, scoresHealthy, wordErrors, scoreErrors, multiLanguageData);

            if (wordsAreHealthy) {
                eventPublisher.publishEvent(new DataReloadedEvent(this));
                var validLanguages = bundles.values().stream()
                        .filter(LanguageBundle::isValid)
                        .count();
                if (scoresHealthy) {
                    log.info("Data loaded successfully. {} valid language(s), scores healthy.", validLanguages);
                } else {
                    log.warn("Score health check failed ({} error(s)); multi-language data loaded ({} valid language(s)). Gameplay available.",
                            scoreErrors.size(), validLanguages);
                }
            } else {
                log.warn("Data loading failed: {} word error(s), {} score error(s).",
                        wordErrors.size(), scoreErrors.size());
            }
        } catch (Exception e) {
            log.error("Data reload failed due to database error: {}", e.getMessage());
            currentSnapshot = DataSnapshot.degraded(List.of("Data reload failed: " + e.getMessage()));
        }
    }

    public DataSnapshot snapshot() {
        return currentSnapshot;
    }

    /**
     * Returns the language codes of all available languages from the repository.
     *
     * @return list of language codes
     */
    public List<String> availableLanguages() {
        return dictionaryRepository.availableLanguages();
    }

    public synchronized void reportRuntimeError(String errorMessage) {
        log.error("Runtime data error: {}", errorMessage);
        currentSnapshot = DataSnapshot.degraded(List.of(errorMessage));
    }

    public synchronized void reportScoreWritePathError(String message) {
        log.error("Score write path error: {}", message);
        var current = currentSnapshot;
        currentSnapshot = new DataSnapshot(current.wordsHealthy(), false, current.wordErrors(), List.of(message), current.multiLanguageData());
    }
}
