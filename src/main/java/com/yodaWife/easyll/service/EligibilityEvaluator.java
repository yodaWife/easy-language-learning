package com.yodawife.easyll.service;

import com.yodawife.easyll.domain.ModeEligibility;
import com.yodawife.easyll.domain.Word;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Stateless evaluator that determines word eligibility for a given game mode,
 * applying global enable/disable flags (FR-020) and per-mode overrides (FR-021).
 */
@Service
public class EligibilityEvaluator {

    /**
     * Determines whether a word is eligible for the given mode.
     *
     * <p>Evaluation order per FR-020 / FR-021:
     * <ol>
     *   <li>If {@code word.globalEnabled()} is {@code false} → ineligible immediately.</li>
     *   <li>If a {@link ModeEligibility} entry exists for the word+mode pair → use its {@code enabled} flag.</li>
     *   <li>Otherwise → eligible by default.</li>
     * </ol>
     *
     * @param word          the word to evaluate
     * @param mode          the game mode name (case-sensitive)
     * @param eligibilities the list of per-mode overrides to consult
     * @return {@code true} if the word is eligible, {@code false} otherwise
     */
    public boolean isEligible(Word word, String mode, List<ModeEligibility> eligibilities) {
        if (!word.globalEnabled()) {
            return false;
        }

        return eligibilities.stream()
                .filter(e -> e.wordId().equals(word.wordId()) && e.mode().equals(mode))
                .findFirst()
                .map(ModeEligibility::enabled)
                .orElse(true);
    }

    /**
     * Filters a list of words down to those eligible for the given mode.
     *
     * @param words         candidate words; the original list is not modified
     * @param mode          the game mode name (case-sensitive)
     * @param eligibilities the list of per-mode overrides to consult
     * @return new unmodifiable list containing only eligible words
     */
    public List<Word> filterEligible(List<Word> words, String mode, List<ModeEligibility> eligibilities) {
        return words.stream()
                .filter(word -> isEligible(word, mode, eligibilities))
                .toList();
    }
}
