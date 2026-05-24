package com.yodawife.easyll.domain;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * All dictionary data for a single language, including words, mode eligibilities,
 * and any validation errors collected during loading.
 *
 * @param languageCode       non-blank BCP-47 language code (e.g. {@code "en"})
 * @param metadata           language display names; may be {@code null} when the
 *                           language failed metadata validation
 * @param words              immutable list of words for this language
 * @param modeEligibilities  immutable list of per-mode eligibility overrides
 * @param validationErrors   immutable list of human-readable validation error messages
 */
public record LanguageBundle(
        String languageCode,
        @Nullable LanguageMetadata metadata,
        List<Word> words,
        List<ModeEligibility> modeEligibilities,
        List<String> validationErrors) {

    public LanguageBundle {
        if (languageCode == null || languageCode.isBlank()) {
            throw new IllegalArgumentException("languageCode must not be blank");
        }
        words = List.copyOf(words);
        modeEligibilities = List.copyOf(modeEligibilities);
        validationErrors = List.copyOf(validationErrors);
    }

    /**
     * Returns {@code true} when no validation errors were recorded for this bundle.
     */
    public boolean isValid() {
        return validationErrors.isEmpty();
    }
}
