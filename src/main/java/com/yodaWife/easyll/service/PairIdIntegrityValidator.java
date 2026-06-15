package com.yodawife.easyll.service;

import com.yodawife.easyll.repository.DictionaryRepository;
import com.yodawife.easyll.repository.ScoreReadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Startup referential-integrity check that verifies every {@code pairId} referenced
 * in the score store exists in at least one loaded language bundle (dictionary).
 *
 * <p>Runs once after the application context is fully started. Orphaned pairIds are
 * logged as WARN; a clean result is logged as INFO. No exceptions are thrown.
 */
@Component
public class PairIdIntegrityValidator {

    private static final Logger log = LoggerFactory.getLogger(PairIdIntegrityValidator.class);

    private final DictionaryRepository dictionaryRepository;
    private final ScoreReadRepository scoreReadRepository;

    public PairIdIntegrityValidator(DictionaryRepository dictionaryRepository, ScoreReadRepository scoreReadRepository) {
        this.dictionaryRepository = dictionaryRepository;
        this.scoreReadRepository = scoreReadRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validate() {
        var languages = dictionaryRepository.availableLanguages();

        if (languages.isEmpty()) {
            log.warn("PairId integrity check skipped: no languages found in dictionary.");
            return;
        }

        Set<String> knownPairIds = languages.stream()
                .map(dictionaryRepository::findLanguage)
                .flatMap(Optional::stream)
                .flatMap(bundle -> bundle.words().stream())
                .map(word -> word.wordId().value())
                .collect(Collectors.toUnmodifiableSet());

        Set<String> scorePairIds = scoreReadRepository.knownUsers().stream()
                .flatMap(userId -> scoreReadRepository.getHistoriesForUser(userId).keySet().stream())
                .collect(Collectors.toUnmodifiableSet());

        if (scorePairIds.isEmpty()) {
            log.info("PairId integrity check: no score entries found, nothing to validate.");
            return;
        }

        var orphans = scorePairIds.stream()
                .filter(pairId -> !knownPairIds.contains(pairId))
                .sorted()
                .toList();

        if (orphans.isEmpty()) {
            log.info("PairId referential integrity check passed: all {} score pairId(s) found in dictionary.",
                    scorePairIds.size());
        } else {
            orphans.forEach(pairId ->
                    log.warn("PairId referential integrity violation: pairId '{}' exists in scores but not in dictionary.", pairId));
            log.warn("PairId referential integrity check found {} orphaned pairId(s) with no matching dictionary entry.",
                    orphans.size());
        }
    }
}
