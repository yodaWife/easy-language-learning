package com.yodawife.easyll.service;

import com.yodawife.easyll.domain.Word;
import com.yodawife.easyll.domain.WordEntry;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

@Service
public class FlashcardService {

    private final DataHealthService dataHealthService;
    private final EligibilityEvaluator eligibilityEvaluator;
    private final RandomGenerator random = RandomGenerator.getDefault();

    public FlashcardService(DataHealthService dataHealthService,
                            EligibilityEvaluator eligibilityEvaluator) {
        this.dataHealthService = dataHealthService;
        this.eligibilityEvaluator = eligibilityEvaluator;
    }

    public Optional<WordEntry> randomCard() {
        DataSnapshot snapshot = dataHealthService.snapshot();
        if (!snapshot.healthy() || snapshot.wordData() == null) {
            return Optional.empty();
        }
        List<WordEntry> words = snapshot.wordData().words();
        if (words.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(words.get(random.nextInt(words.size())));
    }

    /**
     * Returns a random eligible card for the given language and game mode.
     * Falls back to the single-language path when no bundle is found for the language code.
     *
     * @param languageCode the language code to look up in the multi-language data bundle
     * @param mode         the game mode used for eligibility filtering (e.g. "flashcards")
     * @return an eligible {@link WordEntry}, or {@link Optional#empty()} when none are available
     */
    public Optional<WordEntry> randomCard(String languageCode, String mode) {
        var snapshot = dataHealthService.snapshot();
        var bundleOpt = snapshot.getLanguageBundle(languageCode);
        if (bundleOpt.isEmpty()) {
            return Optional.empty();
        }
        var bundle = bundleOpt.get();
        var eligible = eligibilityEvaluator.filterEligible(bundle.words(), mode, bundle.modeEligibilities());
        if (eligible.isEmpty()) {
            return Optional.empty();
        }
        var word = eligible.get(random.nextInt(eligible.size()));
        return Optional.of(new WordEntry(word.fromWord(), word.toWord(), word.example()));
    }

    /**
     * Returns a random eligible word (with its ID) for the given language, mode, and exclusion set.
     *
     * @param languageCode   the language code to look up in the multi-language data bundle
     * @param mode           the game mode used for eligibility filtering (e.g. "flashcards")
     * @param excludeWordIds word IDs to exclude from the eligible pool (e.g. session-learned words)
     * @return an eligible {@link Word}, or {@link Optional#empty()} when none are available
     */
    public Optional<Word> randomWord(String languageCode, String mode, Set<String> excludeWordIds) {
        var snapshot = dataHealthService.snapshot();
        var bundleOpt = snapshot.getLanguageBundle(languageCode);
        if (bundleOpt.isEmpty()) {
            return Optional.empty();
        }
        var bundle = bundleOpt.get();
        var eligible = eligibilityEvaluator.filterEligible(bundle.words(), mode, bundle.modeEligibilities())
                .stream()
                .filter(w -> !excludeWordIds.contains(w.wordId().value()))
                .toList();
        if (eligible.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(eligible.get(random.nextInt(eligible.size())));
    }
}
