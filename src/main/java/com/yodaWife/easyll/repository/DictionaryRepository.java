package com.yodawife.easyll.repository;

import com.yodawife.easyll.domain.LanguageBundle;

import java.util.List;
import java.util.Optional;

/**
 * Read contract for accessing dictionary data by language.
 */
public interface DictionaryRepository {
    Optional<LanguageBundle> findLanguage(String languageCode);
    List<String> availableLanguages();
}
