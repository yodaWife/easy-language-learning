package com.yodawife.easyll.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

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

    private final DataHealthService dataHealthService;

    public PairIdIntegrityValidator(DataHealthService dataHealthService) {
        this.dataHealthService = dataHealthService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validate() {
        var snapshot = dataHealthService.snapshot();
        var scoreData = snapshot.scoreData();
        var multiLanguageData = snapshot.multiLanguageData();

        if (scoreData == null || multiLanguageData == null) {
            log.warn("PairId integrity check skipped: score data or dictionary data is not available.");
            return;
        }

        Set<String> knownPairIds = multiLanguageData.bundles().values().stream()
                .flatMap(bundle -> bundle.words().stream())
                .map(word -> word.wordId().value())
                .collect(Collectors.toUnmodifiableSet());

        Set<String> scorePairIds = scoreData.histories().keySet().stream()
                .map(key -> key.pairId())
                .collect(Collectors.toUnmodifiableSet());

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
