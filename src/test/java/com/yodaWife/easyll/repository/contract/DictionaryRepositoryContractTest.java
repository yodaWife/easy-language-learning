package com.yodawife.easyll.repository.contract;

import com.yodawife.easyll.repository.DictionaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared contract tests that every DictionaryRepository implementation must pass.
 */
public abstract class DictionaryRepositoryContractTest {

    protected DictionaryRepository repository;

    @BeforeEach
    void setUp() {
        repository = createRepository();
    }

    protected abstract DictionaryRepository createRepository();

    /** Subclasses return the language code they pre-populated for tests. */
    protected abstract String getTestLanguageCode();

    @Test
    @DisplayName("findLanguage returns bundle when language exists")
    void findLanguage_returnsBundle_whenLanguageExists() {
        var bundle = repository.findLanguage(getTestLanguageCode());

        assertThat(bundle).isPresent();
        assertThat(bundle.get().languageCode()).isEqualTo(getTestLanguageCode());
        assertThat(bundle.get().words()).isNotEmpty();
    }

    @Test
    @DisplayName("findLanguage returns empty when language not found")
    void findLanguage_returnsEmpty_whenNotFound() {
        assertThat(repository.findLanguage("nonexistent-lang-xyz")).isEmpty();
    }

    @Test
    @DisplayName("availableLanguages returns sorted list")
    void availableLanguages_returnsSortedList() {
        var languages = repository.availableLanguages();

        assertThat(languages).isSortedAccordingTo(String::compareTo);
    }

    @Test
    @DisplayName("availableLanguages returns non-empty list when data exists")
    void availableLanguages_returnsNonEmptyList_whenDataExists() {
        assertThat(repository.availableLanguages()).isNotEmpty();
    }
}
