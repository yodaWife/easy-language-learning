package com.yodawife.easyll.service;

import com.yodawife.easyll.config.DictionaryProperties;
import com.yodawife.easyll.domain.DictionaryOperationResult;
import com.yodawife.easyll.domain.LanguageBundle;
import com.yodawife.easyll.domain.ModeEligibility;
import com.yodawife.easyll.domain.MultiLanguageDataBundle;
import com.yodawife.easyll.domain.Word;
import com.yodawife.easyll.domain.WordId;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DictionaryEditServiceTest {

    @TempDir
    @Nullable Path tempDir;

    private Path tempDir() {
        return Objects.requireNonNull(tempDir);
    }

    private final DataHealthService dataHealthService = mock(DataHealthService.class);
    private final DictionaryWriteLock dictionaryWriteLock = mock(DictionaryWriteLock.class);
    private final CsvPersistence csvPersistence = mock(CsvPersistence.class);
    private final DictionaryAuditLogService auditLogService = mock(DictionaryAuditLogService.class);
    private final DictionaryProperties dictionaryProperties = mock(DictionaryProperties.class);

    private final DictionaryEditService service = new DictionaryEditService(
            dataHealthService, dictionaryWriteLock, csvPersistence, auditLogService, dictionaryProperties);

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

    private DataSnapshot snapshotWith(Word word) {
        var bundle = new LanguageBundle(LANGUAGE, null, List.of(word), List.of(), List.of());
        var multiBundle = new MultiLanguageDataBundle(Map.of(LANGUAGE, bundle), LANGUAGE);
        return new DataSnapshot(true, true, List.of(), List.of(), null, null, multiBundle);
    }

    private DataSnapshot snapshotWith(Word word, ModeEligibility eligibility) {
        var bundle = new LanguageBundle(LANGUAGE, null, List.of(word), List.of(eligibility), List.of());
        var multiBundle = new MultiLanguageDataBundle(Map.of(LANGUAGE, bundle), LANGUAGE);
        return new DataSnapshot(true, true, List.of(), List.of(), null, null, multiBundle);
    }

    @Test
    @DisplayName("toggleGlobalEnabled flips flag, writes CSV, logs audit event, and triggers reload")
    void toggleGlobalEnabledSuccessfullyFlipsFlagAndPersists() throws Exception {
        var word = new Word(WORD_ID, "Hallo", "hello", "", true);
        when(dataHealthService.snapshot()).thenReturn(snapshotWith(word));
        when(dictionaryProperties.getRootPath()).thenReturn(tempDir().toString());

        var result = service.toggleGlobalEnabled(LANGUAGE, WORD_ID);

        assertThat(result).isInstanceOf(DictionaryOperationResult.Success.class);
        var updatedWord = ((DictionaryOperationResult.Success<Word>) result).value();
        assertThat(updatedWord.globalEnabled()).isFalse();
        verify(csvPersistence).writeWords(any(Path.class), any());
        verify(auditLogService).logGlobalToggle(LANGUAGE, WORD_ID, true, false);
        verify(dataHealthService).reload();
    }

    @Test
    @DisplayName("toggleGlobalEnabled returns Failure when the language code is not found")
    void toggleGlobalEnabledReturnsFailureWhenLanguageNotFound() {
        when(dataHealthService.snapshot()).thenReturn(DataSnapshot.degraded(List.of()));

        var result = service.toggleGlobalEnabled("unknown", WORD_ID);

        assertThat(result).isInstanceOf(DictionaryOperationResult.Failure.class);
        assertThat(((DictionaryOperationResult.Failure<Word>) result).errorMessage())
                .contains("Language not found");
    }

    @Test
    @DisplayName("toggleGlobalEnabled returns Failure when the word is not found in the bundle")
    void toggleGlobalEnabledReturnsFailureWhenWordNotFound() {
        var differentWord = new Word(new WordId("other"), "Andere", "other", "", true);
        when(dataHealthService.snapshot()).thenReturn(snapshotWith(differentWord));

        var result = service.toggleGlobalEnabled(LANGUAGE, WORD_ID);

        assertThat(result).isInstanceOf(DictionaryOperationResult.Failure.class);
        assertThat(((DictionaryOperationResult.Failure<Word>) result).errorMessage())
                .contains("Word not found");
    }

    @Test
    @DisplayName("toggleGlobalEnabled returns Failure when CsvPersistence throws IOException")
    void toggleGlobalEnabledReturnsFailureOnIoException() throws Exception {
        var word = new Word(WORD_ID, "Hallo", "hello", "", true);
        when(dataHealthService.snapshot()).thenReturn(snapshotWith(word));
        when(dictionaryProperties.getRootPath()).thenReturn(tempDir().toString());
        doThrow(new IOException("disk full")).when(csvPersistence).writeWords(any(), any());

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
        when(dataHealthService.snapshot()).thenReturn(snapshotWith(word, existingEligibility));
        when(dictionaryProperties.getRootPath()).thenReturn(tempDir().toString());

        var result = service.toggleModeEnabled(LANGUAGE, WORD_ID, MODE);

        assertThat(result).isInstanceOf(DictionaryOperationResult.Success.class);
        var updated = ((DictionaryOperationResult.Success<ModeEligibility>) result).value();
        assertThat(updated.enabled()).isFalse();
    }

    @Test
    @DisplayName("toggleModeEnabled with no existing entry defaults to enabled=true, then flips to false")
    void toggleModeEnabledMissingEntryDefaultsTrueThenFlipsToFalse() throws Exception {
        var word = new Word(WORD_ID, "Hallo", "hello", "", true);
        when(dataHealthService.snapshot()).thenReturn(snapshotWith(word));
        when(dictionaryProperties.getRootPath()).thenReturn(tempDir().toString());

        var result = service.toggleModeEnabled(LANGUAGE, WORD_ID, MODE);

        assertThat(result).isInstanceOf(DictionaryOperationResult.Success.class);
        var updated = ((DictionaryOperationResult.Success<ModeEligibility>) result).value();
        assertThat(updated.enabled()).isFalse();
    }
}
