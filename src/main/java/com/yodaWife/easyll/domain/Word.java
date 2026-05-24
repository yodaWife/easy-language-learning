package com.yodawife.easyll.domain;

/**
 * A dictionary word with its translation and optional usage example.
 *
 * <p>This is a richer replacement for {@link WordEntry} in the dictionary-management
 * feature; {@link WordEntry} is kept untouched for backward compatibility.
 *
 * @param wordId         strongly-typed identifier
 * @param fromWord       trimmed, non-blank source-language word
 * @param toWord         trimmed, non-blank target-language word
 * @param example        optional usage example; normalised to empty string when null
 * @param globalEnabled  whether the word is globally enabled across all modes
 */
public record Word(WordId wordId, String fromWord, String toWord, String example, boolean globalEnabled) {

    public Word {
        if (wordId == null) {
            throw new IllegalArgumentException("wordId must not be null");
        }
        if (fromWord == null || fromWord.isBlank()) {
            throw new IllegalArgumentException("fromWord must not be blank");
        }
        if (toWord == null || toWord.isBlank()) {
            throw new IllegalArgumentException("toWord must not be blank");
        }
        fromWord = fromWord.trim();
        toWord = toWord.trim();
        example = (example == null) ? "" : example.trim();
    }
}
