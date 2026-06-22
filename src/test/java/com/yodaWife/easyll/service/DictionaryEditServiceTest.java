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

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DictionaryEditServiceTest {

    private final DataHealthService dataHealthService = mock(DataHealthService.class);
    private final DictionaryWriteLock dictionaryWriteLock = mock(DictionaryWriteLock.class);
    private final DictionaryRepository dictionaryRepository = mock(DictionaryRepository.class);
    private final DictionaryAuditLogService auditLogService = mock(DictionaryAuditLogService.class);
    private final CsvDictionaryParser csvDictionaryParser = mock(CsvDictionaryParser.class);

    private final DictionaryEditService service = new DictionaryEditService(
            dataHealthService, dictionaryWriteLock, dictionaryRepository, auditLogService, csvDictionaryParser);

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

    // -------------------------------------------------------------------------
    // uploadCsvDictionary tests
    // -------------------------------------------------------------------------

    private LanguageBundle emptyBundle() {
        return new LanguageBundle(LANGUAGE, null, List.of(), List.of(), List.of());
    }

    @Test
    @DisplayName("uploadCsvDictionary imports 2 new words and returns Success(imported=2, skipped=0)")
    void uploadCsvDictionaryWithTwoNewWordsImportsBothAndReturnsSuccess() {
        var rows = List.of(
                new CsvDictionaryParser.ParsedWordRow("Apple", "Alma", ""),
                new CsvDictionaryParser.ParsedWordRow("Cat", "Macska", "")
        );
        when(csvDictionaryParser.parse(anyString()))
                .thenReturn(new CsvDictionaryParser.ParseResult.Success(rows, 0, List.of()));
        when(dictionaryRepository.findLanguage(LANGUAGE)).thenReturn(Optional.of(emptyBundle()));

        var result = service.uploadCsvDictionary(LANGUAGE, "csv-content");

        assertThat(result).isInstanceOf(DictionaryOperationResult.Success.class);
        var summary = ((DictionaryOperationResult.Success<DictionaryEditService.CsvUploadSummary>) result).value();
        assertThat(summary.imported()).isEqualTo(2);
        assertThat(summary.skipped()).isEqualTo(0);
        verify(dictionaryRepository, times(2)).insertWord(eq(LANGUAGE), anyString(), anyString(), anyString(), anyString(), anyBoolean());
        verify(dataHealthService).reload();
    }

    @Test
    @DisplayName("uploadCsvDictionary skips a word already present in the bundle and imports only the new one")
    void uploadCsvDictionarySkipsDuplicateAgainstExistingBundle() {
        var existingWord = new Word(new WordId("existing-id"), "Apple", "Alma", "", true);
        var rows = List.of(
                new CsvDictionaryParser.ParsedWordRow("Apple", "Alma", ""),
                new CsvDictionaryParser.ParsedWordRow("Cat", "Macska", "")
        );
        when(csvDictionaryParser.parse(anyString()))
                .thenReturn(new CsvDictionaryParser.ParseResult.Success(rows, 0, List.of()));
        when(dictionaryRepository.findLanguage(LANGUAGE)).thenReturn(Optional.of(bundleWith(existingWord)));

        var result = service.uploadCsvDictionary(LANGUAGE, "csv-content");

        assertThat(result).isInstanceOf(DictionaryOperationResult.Success.class);
        var summary = ((DictionaryOperationResult.Success<DictionaryEditService.CsvUploadSummary>) result).value();
        assertThat(summary.imported()).isEqualTo(1);
        assertThat(summary.skipped()).isEqualTo(1);
        verify(dictionaryRepository).insertWord(eq(LANGUAGE), anyString(), eq("Cat"), eq("Macska"), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("uploadCsvDictionary returns Failure when the parser rejects the CSV file")
    void uploadCsvDictionaryReturnsFailureWhenParserFails() {
        when(csvDictionaryParser.parse(anyString()))
                .thenReturn(new CsvDictionaryParser.ParseResult.Failure("Invalid CSV header"));

        var result = service.uploadCsvDictionary(LANGUAGE, "bad-csv");

        assertThat(result).isInstanceOf(DictionaryOperationResult.Failure.class);
        assertThat(((DictionaryOperationResult.Failure<DictionaryEditService.CsvUploadSummary>) result).errorMessage())
                .contains("Invalid CSV header");
        verifyNoInteractions(dictionaryRepository);
    }

    @Test
    @DisplayName("uploadCsvDictionary returns Failure when ParseResult.Success contains row errors (FR-CSV-003)")
    void uploadCsvDictionaryReturnsFailureWhenParserSuccessHasRowErrors() {
        when(csvDictionaryParser.parse(anyString()))
                .thenReturn(new CsvDictionaryParser.ParseResult.Success(List.of(), 0, List.of("Line 3: Expected 3 columns but found 2")));

        var result = service.uploadCsvDictionary(LANGUAGE, "csv-content");

        assertThat(result).isInstanceOf(DictionaryOperationResult.Failure.class);
        assertThat(((DictionaryOperationResult.Failure<DictionaryEditService.CsvUploadSummary>) result).errorMessage())
                .contains("Upload rejected due to invalid rows");
        verify(dictionaryRepository, never()).findLanguage(anyString());
        verify(dictionaryRepository, never()).insertWord(anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("uploadCsvDictionary returns Failure when the language code is not found in the repository")
    void uploadCsvDictionaryReturnsFailureWhenLanguageNotFound() {
        var rows = List.of(new CsvDictionaryParser.ParsedWordRow("Apple", "Alma", ""));
        when(csvDictionaryParser.parse(anyString()))
                .thenReturn(new CsvDictionaryParser.ParseResult.Success(rows, 0, List.of()));
        when(dictionaryRepository.findLanguage("unknown")).thenReturn(Optional.empty());

        var result = service.uploadCsvDictionary("unknown", "csv-content");

        assertThat(result).isInstanceOf(DictionaryOperationResult.Failure.class);
        verify(dictionaryRepository, never()).insertWord(anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("uploadCsvDictionary returns Success(imported=0, skipped=N) when all rows are in-file duplicates")
    void uploadCsvDictionaryReturnsSuccessWithZeroImportsWhenAllRowsAreInFileDuplicates() {
        when(csvDictionaryParser.parse(anyString()))
                .thenReturn(new CsvDictionaryParser.ParseResult.Success(List.of(), 3, List.of()));

        var result = service.uploadCsvDictionary(LANGUAGE, "csv-content");

        assertThat(result).isInstanceOf(DictionaryOperationResult.Success.class);
        var summary = ((DictionaryOperationResult.Success<DictionaryEditService.CsvUploadSummary>) result).value();
        assertThat(summary.imported()).isEqualTo(0);
        assertThat(summary.skipped()).isEqualTo(3);
        verifyNoInteractions(dictionaryRepository);
    }

    @Test
    @DisplayName("uploadCsvDictionary returns Failure when insertWord throws a RuntimeException")
    void uploadCsvDictionaryReturnsFailureWhenInsertWordThrows() {
        var rows = List.of(new CsvDictionaryParser.ParsedWordRow("Apple", "Alma", ""));
        when(csvDictionaryParser.parse(anyString()))
                .thenReturn(new CsvDictionaryParser.ParseResult.Success(rows, 0, List.of()));
        when(dictionaryRepository.findLanguage(LANGUAGE)).thenReturn(Optional.of(emptyBundle()));
        doThrow(new RuntimeException("DB connection refused"))
                .when(dictionaryRepository).insertWord(anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean());

        var result = service.uploadCsvDictionary(LANGUAGE, "csv-content");

        assertThat(result).isInstanceOf(DictionaryOperationResult.Failure.class);
        assertThat(((DictionaryOperationResult.Failure<DictionaryEditService.CsvUploadSummary>) result).errorMessage())
                .contains("Failed to save changes");
    }

    @Test
    @DisplayName("uploadCsvDictionary returns Failure when the write lock cannot be acquired within the timeout")
    void uploadCsvDictionaryReturnsFailureWhenLockTimesOut() throws Exception {
        var rows = List.of(new CsvDictionaryParser.ParsedWordRow("Apple", "Alma", ""));
        when(csvDictionaryParser.parse(anyString()))
                .thenReturn(new CsvDictionaryParser.ParseResult.Success(rows, 0, List.of()));
        when(dictionaryRepository.findLanguage(LANGUAGE)).thenReturn(Optional.of(emptyBundle()));
        doThrow(new DictionaryWriteLock.DictionaryLockTimeoutException(LANGUAGE))
                .when(dictionaryWriteLock).executeWithLock(anyString(), anyLong(), any(Runnable.class));

        var result = service.uploadCsvDictionary(LANGUAGE, "csv-content");

        assertThat(result).isInstanceOf(DictionaryOperationResult.Failure.class);
        assertThat(((DictionaryOperationResult.Failure<DictionaryEditService.CsvUploadSummary>) result).errorMessage())
                .contains("Dictionary is busy");
    }

    @Test
    @DisplayName("uploadCsvDictionary inserts each new word with globalEnabled=true")
    void uploadCsvDictionaryInsertsWordsWithGlobalEnabledTrue() {
        var rows = List.of(
                new CsvDictionaryParser.ParsedWordRow("Apple", "Alma", ""),
                new CsvDictionaryParser.ParsedWordRow("Cat", "Macska", "")
        );
        when(csvDictionaryParser.parse(anyString()))
                .thenReturn(new CsvDictionaryParser.ParseResult.Success(rows, 0, List.of()));
        when(dictionaryRepository.findLanguage(LANGUAGE)).thenReturn(Optional.of(emptyBundle()));

        service.uploadCsvDictionary(LANGUAGE, "csv-content");

        var globalEnabledCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(dictionaryRepository, times(2))
                .insertWord(anyString(), anyString(), anyString(), anyString(), anyString(), globalEnabledCaptor.capture());
        assertThat(globalEnabledCaptor.getAllValues()).containsOnly(true);
    }

    @Test
    @DisplayName("uploadCsvDictionary assigns a distinct UUID-based pair_id to each imported word")
    void uploadCsvDictionaryAssignsDistinctUuidsToEachWord() {
        var rows = List.of(
                new CsvDictionaryParser.ParsedWordRow("Apple", "Alma", ""),
                new CsvDictionaryParser.ParsedWordRow("Cat", "Macska", "")
        );
        when(csvDictionaryParser.parse(anyString()))
                .thenReturn(new CsvDictionaryParser.ParseResult.Success(rows, 0, List.of()));
        when(dictionaryRepository.findLanguage(LANGUAGE)).thenReturn(Optional.of(emptyBundle()));

        service.uploadCsvDictionary(LANGUAGE, "csv-content");

        var pairIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(dictionaryRepository, times(2))
                .insertWord(anyString(), pairIdCaptor.capture(), anyString(), anyString(), anyString(), anyBoolean());
        assertThat(pairIdCaptor.getAllValues()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("uploadCsvDictionary total skipped count combines in-file duplicates and against-existing duplicates")
    void uploadCsvDictionaryTotalSkippedCombinesInFileAndAgainstExistingSkips() {
        var existingWord = new Word(new WordId("existing-id"), "Apple", "Alma", "", true);
        var rows = List.of(
                new CsvDictionaryParser.ParsedWordRow("Apple", "Alma", ""),
                new CsvDictionaryParser.ParsedWordRow("Cat", "Macska", "")
        );
        when(csvDictionaryParser.parse(anyString()))
                .thenReturn(new CsvDictionaryParser.ParseResult.Success(rows, 2, List.of()));
        when(dictionaryRepository.findLanguage(LANGUAGE)).thenReturn(Optional.of(bundleWith(existingWord)));

        var result = service.uploadCsvDictionary(LANGUAGE, "csv-content");

        assertThat(result).isInstanceOf(DictionaryOperationResult.Success.class);
        var summary = ((DictionaryOperationResult.Success<DictionaryEditService.CsvUploadSummary>) result).value();
        assertThat(summary.imported()).isEqualTo(1);
        assertThat(summary.skipped()).isEqualTo(3);
    }
}
