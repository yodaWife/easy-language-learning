package com.yodawife.easyll.domain;

/**
 * Canonical key for a single user-word-mode score entry.
 *
 * @param userId  the stable user identifier (UUID string from {@link Account#userId()})
 * @param pairId  the stable word-pair identifier (= dictionary {@link WordId#value()})
 * @param mode    game mode string (e.g. "match", "flashcards")
 */
public record ScoreKey(String userId, String pairId, String mode) {

    public ScoreKey {
        if (userId == null || userId.isBlank())  throw new IllegalArgumentException("userId must not be blank");
        if (pairId == null || pairId.isBlank())  throw new IllegalArgumentException("pairId must not be blank");
        if (mode   == null || mode.isBlank())    throw new IllegalArgumentException("mode must not be blank");
    }
}
