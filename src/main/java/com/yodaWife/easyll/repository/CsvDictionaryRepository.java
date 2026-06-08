package com.yodawife.easyll.repository;

import com.yodawife.easyll.config.PersistenceProfiles;
import com.yodawife.easyll.domain.LanguageBundle;
import com.yodawife.easyll.service.DataHealthService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * CSV-backed implementation of {@link DictionaryRepository}.
 * Delegates to {@link DataHealthService} which owns the loaded CSV state.
 */
@Repository
@Profile(PersistenceProfiles.CSV)
public class CsvDictionaryRepository implements DictionaryRepository {

    private final DataHealthService dataHealthService;

    public CsvDictionaryRepository(DataHealthService dataHealthService) {
        this.dataHealthService = dataHealthService;
    }

    @Override
    public Optional<LanguageBundle> findLanguage(String languageCode) {
        return dataHealthService.snapshot().getLanguageBundle(languageCode);
    }

    @Override
    public List<String> availableLanguages() {
        return dataHealthService.availableLanguages();
    }
}
