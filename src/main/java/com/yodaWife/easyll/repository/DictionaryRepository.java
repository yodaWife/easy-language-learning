package com.yodawife.easyll.repository;

import com.yodawife.easyll.domain.LanguageBundle;

import java.util.List;
import java.util.Optional;

/**
 * Read/write contract for accessing dictionary data by language.
 */
public interface DictionaryRepository {
    Optional<LanguageBundle> findLanguage(String languageCode);
    List<String> availableLanguages();

    void updateGlobalEnabled(String pairId, boolean enabled);
    void updateWordContent(String pairId, String fromWord, String toWord, String example);
    void insertWord(String languageCode, String pairId, String fromWord, String toWord, String example, boolean globalEnabled);
    void upsertModeEligibility(String pairId, String mode, boolean enabled);
}
