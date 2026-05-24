package com.yodawife.easyll.domain;

/**
 * Represents whether a specific {@link Word} is eligible for a given game mode.
 *
 * @param wordId  the identifier of the word
 * @param mode    the trimmed, non-blank name of the game mode
 * @param enabled whether the word is enabled for the mode
 */
public record ModeEligibility(WordId wordId, String mode, boolean enabled) {

    public ModeEligibility {
        if (wordId == null) {
            throw new IllegalArgumentException("wordId must not be null");
        }
        if (mode == null || mode.isBlank()) {
            throw new IllegalArgumentException("mode must not be blank");
        }
        mode = mode.trim();
    }
}
