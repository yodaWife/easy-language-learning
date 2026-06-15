package com.yodawife.easyll.service;

import com.yodawife.easyll.domain.DictionaryOperationResult;
import com.yodawife.easyll.domain.LanguageBundle;
import com.yodawife.easyll.domain.ModeEligibility;
import com.yodawife.easyll.domain.Word;
import com.yodawife.easyll.domain.WordId;
import com.yodawife.easyll.repository.DictionaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DictionaryEditServiceTest {

    private final DataHealthService dataHealthService = mock(DataHealthService.class);
    private final DictionaryWriteLock dictionaryWriteLock = mock(DictionaryWriteLock.class);
    private final DictionaryRepository dictionaryRepository = mock(DictionaryRepository.class);
    private final DictionaryAuditLogService auditLogService = mock(DictionaryAuditLogService.class);

    private final DictionaryEditService service = new DictionaryEditService(
            dataHealthService, dictionaryWriteLock, dictionaryRepository, auditLogService);

    private static final String LANGUAGE = "en";
    private static final WordId WORD_ID = new WordId("hello");
    private static final String MODE = "flashcards";

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            Runnable action = inv.getArgument(2);
            action.run();
            return null;
        }).when(dictionaryWriteLock).executeWithLock(anyString(), anyLong(), any(Runnable.class));
    }

    private LanguageBundle bundleWith(Word word) {
        return new LanguageBundle(LANGUAGE, null, List.of(word), List.of(), List.of());
    }

    private LanguageBundle bundleWith(Word word, ModeEligibility eligibility) {
        return new LanguageBundle(LANGUAGE, null, List.of(word), List.of(eligibility), List.of());
    }

    @Test
    @DisplayName("toggleGlobalEnabled flips flag, writes to database, logs audit event, and triggers reload")
    void toggleGlobalEnabledSuccessfullyFlipsFlagAndPersists() throws Exception {
        var word = new Word(WORD_ID, "Hallo", "hello", "", true);
        when(dictionaryRepository.findLanguage(LANGUAGE)).thenReturn(Optional.of(bundleWith(word)));

        var result = service.toggleGlobalEnabled(LANGUAGE, WORD_ID);

        assertThat(result).isInstanceOf(DictionaryOperationResult.Success.class);
        var updatedWord = ((DictionaryOperationResult.Success<Word>) result).value();
        assertThat(updatedWord.globalEnabled()).isFalse();
        verify(dictionaryRepository).updateGlobalEnabled(WORD_ID.value(), false);
        verify(auditLogService).logGlobalToggle(LANGUAGE, WORD_ID, true, false);
        verify(dataHealthService).reload();
    }

    @Test
    @DisplayName("toggleGlobalEnabled returns Failure when the language code is not found")
    void toggleGlobalEnabledReturnsFailureWhenLanguageNotFound() {
        when(dictionaryRepository.findLanguage("unknown")).thenReturn(Optional.empty());

        var result = service.toggleGlobalEnabled("unknown", WORD_ID);

        assertThat(result).isInstanceOf(DictionaryOperationResult.Failure.class);
        assertThat(((DictionaryOperationResult.Failure<Word>) result).errorMessage())
                .contains("Language not found");
    }

    @Test
    @DisplayName("toggleGlobalEnabled returns Failure when the word is not found in the bundle")
    void toggleGlobalEnabledReturnsFailureWhenWordNotFound() {
        var differentWord = new Word(new WordId("other"), "Andere", "other", "", true);
        when(dictionaryRepository.findLanguage(LANGUAGE)).thenReturn(Optional.of(bundleWith(differentWord)));

        var result = service.toggleGlobalEnabled(LANGUAGE, WORD_ID);

        assertThat(result).isInstanceOf(DictionaryOperationResult.Failure.class);
        assertThat(((DictionaryOperationResult.Failure<Word>) result).errorMessage())
                .contains("Word not found");
    }

    @Test
    @DisplayName("toggleGlobalEnabled returns Failure when database throws an exception")
    void toggleGlobalEnabledReturnsFailureOnDatabaseException() {
        var word = new Word(WORD_ID, "Hallo", "hello", "", true);
        when(dictionaryRepository.findLanguage(LANGUAGE)).thenReturn(Optional.of(bundleWith(word)));
        doThrow(new RuntimeException("connection refused")).when(dictionaryRepository).updateGlobalEnabled(anyString(), anyBoolean());

        var result = service.toggleGlobalEnabled(LANGUAGE, WORD_ID);

        assertThat(result).isInstanceOf(DictionaryOperationResult.Failure.class);
        assertThat(((DictionaryOperationResult.Failure<Word>) result).errorMessage())
                .contains("Failed to save changes");
    }

    @Test
    @DisplayName("toggleModeEnabled flips an existing mode entry from true to false")
    void toggleModeEnabledFlipsExistingEntryFromTrueToFalse() throws Exception {
        var word = new Word(WORD_ID, "Hallo", "hello", "", true);
        var existingEligibility = new ModeEligibility(WORD_ID, MODE, true);
        when(dictionaryRepository.findLanguage(LANGUAGE)).thenReturn(Optional.of(bundleWith(word, existingEligibility)));

        var result = service.toggleModeEnabled(LANGUAGE, WORD_ID, MODE);

        assertThat(result).isInstanceOf(DictionaryOperationResult.Success.class);
        var updated = ((DictionaryOperationResult.Success<ModeEligibility>) result).value();
        assertThat(updated.enabled()).isFalse();
    }

    @Test
    @DisplayName("toggleModeEnabled with no existing entry defaults to enabled=true, then flips to false")
    void toggleModeEnabledMissingEntryDefaultsTrueThenFlipsToFalse() throws Exception {
        var word = new Word(WORD_ID, "Hallo", "hello", "", true);
        when(dictionaryRepository.findLanguage(LANGUAGE)).thenReturn(Optional.of(bundleWith(word)));

        var result = service.toggleModeEnabled(LANGUAGE, WORD_ID, MODE);

        assertThat(result).isInstanceOf(DictionaryOperationResult.Success.class);
        var updated = ((DictionaryOperationResult.Success<ModeEligibility>) result).value();
        assertThat(updated.enabled()).isFalse();
    }
}
