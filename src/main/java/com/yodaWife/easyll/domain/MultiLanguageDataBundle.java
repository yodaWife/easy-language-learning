package com.yodawife.easyll.domain;

import java.util.Map;
import java.util.Optional;

/**
 * Container for all language bundles loaded by the application, with a
 * designated primary language.
 *
 * @param bundles              immutable map of language-code → {@link LanguageBundle}
 * @param primaryLanguageCode  non-blank language code identifying the primary language
 */
public record MultiLanguageDataBundle(Map<String, LanguageBundle> bundles, String primaryLanguageCode) {

    public MultiLanguageDataBundle {
        if (primaryLanguageCode == null || primaryLanguageCode.isBlank()) {
            throw new IllegalArgumentException("primaryLanguageCode must not be blank");
        }
        bundles = Map.copyOf(bundles);
    }

    /**
     * Returns the {@link LanguageBundle} for the given language code, or an empty
     * {@link Optional} if no bundle is registered for that code.
     *
     * @param languageCode the language code to look up
     * @return optional bundle for the requested language
     */
    public Optional<LanguageBundle> getBundle(String languageCode) {
        return Optional.ofNullable(bundles.get(languageCode));
    }

    /**
     * Returns {@code true} when a bundle exists for the given language code and
     * that bundle has no validation errors.
     *
     * @param languageCode the language code to check
     * @return {@code true} if the bundle is present and valid
     */
    public boolean hasValidLanguage(String languageCode) {
        var bundle = bundles.get(languageCode);
        return bundle != null && bundle.isValid();
    }
}
