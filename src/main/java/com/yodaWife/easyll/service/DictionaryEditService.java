package com.yodawife.easyll.service;

import com.yodawife.easyll.domain.DictionaryOperationResult;
import com.yodawife.easyll.domain.ModeEligibility;
import com.yodawife.easyll.domain.Word;
import com.yodawife.easyll.domain.WordId;
import com.yodawife.easyll.repository.DictionaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Handles dictionary toggle operations (global and mode-scoped) with per-language write locking,
 * atomic database persistence, audit logging, and in-memory snapshot refresh.
 */
@Service
public class DictionaryEditService {

    private static final Logger log = LoggerFactory.getLogger(DictionaryEditService.class);
    private static final long LOCK_TIMEOUT_MS = 5_000L;

    private final DataHealthService dataHealthService;
    private final DictionaryWriteLock dictionaryWriteLock;
    private final DictionaryRepository dictionaryRepository;
    private final DictionaryAuditLogService auditLogService;

    public DictionaryEditService(
            DataHealthService dataHealthService,
            DictionaryWriteLock dictionaryWriteLock,
            DictionaryRepository dictionaryRepository,
            DictionaryAuditLogService auditLogService) {
        this.dataHealthService = dataHealthService;
        this.dictionaryWriteLock = dictionaryWriteLock;
        this.dictionaryRepository = dictionaryRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * Toggles the {@code globalEnabled} flag of the specified word and persists the change atomically.
     *
     * @param languageCode the BCP-47 language code identifying the dictionary
     * @param wordId       the identifier of the word whose flag should be toggled
     * @return {@link DictionaryOperationResult.Success} carrying the updated {@link Word},
     *         or {@link DictionaryOperationResult.Failure} with a descriptive message on any error
     */
    public DictionaryOperationResult<Word> toggleGlobalEnabled(String languageCode, WordId wordId) {
        var bundleOpt = dictionaryRepository.findLanguage(languageCode);
        if (bundleOpt.isEmpty()) {
            return new DictionaryOperationResult.Failure<>("Language not found: " + languageCode);
        }

        var bundle = bundleOpt.get();
        var wordOpt = bundle.words().stream()
                .filter(w -> w.wordId().equals(wordId))
                .findFirst();

        if (wordOpt.isEmpty()) {
            return new DictionaryOperationResult.Failure<>("Word not found: " + wordId.value());
        }

        var word = wordOpt.get();
        boolean newEnabled = !word.globalEnabled();
        var updatedWord = new Word(word.wordId(), word.fromWord(), word.toWord(), word.example(), newEnabled);

        try {
            dictionaryWriteLock.executeWithLock(languageCode, LOCK_TIMEOUT_MS, () -> {
                dictionaryRepository.updateGlobalEnabled(wordId.value(), newEnabled);
                auditLogService.logGlobalToggle(languageCode, wordId, word.globalEnabled(), newEnabled);
                dataHealthService.reload();
            });
        } catch (DictionaryWriteLock.DictionaryLockTimeoutException e) {
            return new DictionaryOperationResult.Failure<>("Dictionary is busy, please try again");
        } catch (RuntimeException e) {
            log.warn("Failed to update database for language '{}': {}", languageCode, e.getMessage());
            return new DictionaryOperationResult.Failure<>("Failed to save changes: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new DictionaryOperationResult.Failure<>("Operation interrupted");
        }

        return new DictionaryOperationResult.Success<>(updatedWord);
    }

    /**
     * Toggles the {@code enabled} flag for the specified word in the given game mode and persists
     * the change atomically.
     *
     * <p>If no explicit {@link ModeEligibility} entry exists for the word/mode combination,
     * the implicit default of {@code enabled=true} (FR-021) is assumed, so the first explicit
     * toggle sets the value to {@code false}.
     *
     * @param languageCode the BCP-47 language code identifying the dictionary
     * @param wordId       the identifier of the word whose mode eligibility should be toggled
     * @param mode         the game mode to toggle for
     * @return {@link DictionaryOperationResult.Success} carrying the updated {@link ModeEligibility},
     *         or {@link DictionaryOperationResult.Failure} with a descriptive message on any error
     */
    public DictionaryOperationResult<ModeEligibility> toggleModeEnabled(String languageCode, WordId wordId, String mode) {
        var bundleOpt = dictionaryRepository.findLanguage(languageCode);
        if (bundleOpt.isEmpty()) {
            return new DictionaryOperationResult.Failure<>("Language not found");
        }

        var bundle = bundleOpt.get();
        boolean wordExists = bundle.words().stream().anyMatch(w -> w.wordId().equals(wordId));
        if (!wordExists) {
            return new DictionaryOperationResult.Failure<>("Word not found: " + wordId.value());
        }

        var existingOpt = bundle.modeEligibilities().stream()
                .filter(me -> me.wordId().equals(wordId) && me.mode().equals(mode))
                .findFirst();

        // FR-021: implicit default is enabled=true; first explicit toggle sets it to false
        var existing = existingOpt.orElse(new ModeEligibility(wordId, mode, true));
        boolean newEnabled = !existing.enabled();
        var updatedEligibility = new ModeEligibility(wordId, mode, newEnabled);

        try {
            dictionaryWriteLock.executeWithLock(languageCode, LOCK_TIMEOUT_MS, () -> {
                dictionaryRepository.upsertModeEligibility(wordId.value(), mode, newEnabled);
                auditLogService.logModeToggle(languageCode, wordId, mode, existing.enabled(), newEnabled);
                dataHealthService.reload();
            });
        } catch (DictionaryWriteLock.DictionaryLockTimeoutException e) {
            return new DictionaryOperationResult.Failure<>("Dictionary is busy, please try again");
        } catch (RuntimeException e) {
            log.warn("Failed to update database for language '{}': {}", languageCode, e.getMessage());
            return new DictionaryOperationResult.Failure<>("Failed to save changes: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new DictionaryOperationResult.Failure<>("Operation interrupted");
        }

        return new DictionaryOperationResult.Success<>(updatedEligibility);
    }

    /**
     * Explicitly sets the {@code enabled} flag for the specified word in the given game mode
     * and persists the change atomically. If the word is already at the desired state, the call
     * is a no-op and returns success immediately.
     *
     * @param languageCode the BCP-47 language code identifying the dictionary
     * @param wordId       the identifier of the word whose mode eligibility should be set
     * @param mode         the game mode to set eligibility for
     * @param enabled      the desired eligibility value
     * @return {@link DictionaryOperationResult.Success} carrying the resulting {@link ModeEligibility},
     *         or {@link DictionaryOperationResult.Failure} with a descriptive message on any error
     */
    public DictionaryOperationResult<ModeEligibility> setModeEnabled(
            String languageCode, WordId wordId, String mode, boolean enabled) {
        var bundleOpt = dictionaryRepository.findLanguage(languageCode);
        if (bundleOpt.isEmpty()) {
            return new DictionaryOperationResult.Failure<>("Language not found");
        }

        var bundle = bundleOpt.get();
        boolean wordExists = bundle.words().stream().anyMatch(w -> w.wordId().equals(wordId));
        if (!wordExists) {
            return new DictionaryOperationResult.Failure<>("Word not found: " + wordId.value());
        }

        var existingOpt = bundle.modeEligibilities().stream()
                .filter(me -> me.wordId().equals(wordId) && me.mode().equals(mode))
                .findFirst();
        boolean oldEnabled = existingOpt.map(ModeEligibility::enabled).orElse(true);

        // No-op if the word is already at the desired state
        if (oldEnabled == enabled) {
            return new DictionaryOperationResult.Success<>(new ModeEligibility(wordId, mode, enabled));
        }

        var updatedEligibility = new ModeEligibility(wordId, mode, enabled);

        try {
            dictionaryWriteLock.executeWithLock(languageCode, LOCK_TIMEOUT_MS, () -> {
                dictionaryRepository.upsertModeEligibility(wordId.value(), mode, enabled);
                auditLogService.logModeToggle(languageCode, wordId, mode, oldEnabled, enabled);
                dataHealthService.reload();
            });
        } catch (DictionaryWriteLock.DictionaryLockTimeoutException e) {
            return new DictionaryOperationResult.Failure<>("Dictionary is busy, please try again");
        } catch (RuntimeException e) {
            log.warn("Failed to update database for language '{}': {}", languageCode, e.getMessage());
            return new DictionaryOperationResult.Failure<>("Failed to save changes: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new DictionaryOperationResult.Failure<>("Operation interrupted");
        }

        return new DictionaryOperationResult.Success<>(updatedEligibility);
    }

    /**
     * Updates the text content (fromWord, toWord, example) of a word and persists the change atomically.
     *
     * @param languageCode the BCP-47 language code identifying the dictionary
     * @param wordId       the identifier of the word to edit
     * @param newFromWord  new source-language word (must not be blank)
     * @param newToWord    new target-language word (must not be blank)
     * @param newExample   new usage example (may be empty)
     * @return {@link DictionaryOperationResult.Success} carrying the updated {@link Word},
     *         or {@link DictionaryOperationResult.Failure} with a descriptive message on any error
     */
    public DictionaryOperationResult<Word> editWord(String languageCode, WordId wordId,
                                                    String newFromWord, String newToWord, String newExample) {
        var bundleOpt = dictionaryRepository.findLanguage(languageCode);
        if (bundleOpt.isEmpty()) {
            return new DictionaryOperationResult.Failure<>("Language not found: " + languageCode);
        }

        var bundle = bundleOpt.get();
        var wordOpt = bundle.words().stream()
                .filter(w -> w.wordId().equals(wordId))
                .findFirst();

        if (wordOpt.isEmpty()) {
            return new DictionaryOperationResult.Failure<>("Word not found: " + wordId.value());
        }

        var word = wordOpt.get();
        var updatedWord = new Word(word.wordId(), newFromWord.trim(), newToWord.trim(),
                newExample == null ? "" : newExample.trim(), word.globalEnabled());

        try {
            dictionaryWriteLock.executeWithLock(languageCode, LOCK_TIMEOUT_MS, () -> {
                dictionaryRepository.updateWordContent(wordId.value(), updatedWord.fromWord(), updatedWord.toWord(), updatedWord.example());
                auditLogService.logWordEdit(languageCode, wordId, word, updatedWord);
                dataHealthService.reload();
            });
        } catch (DictionaryWriteLock.DictionaryLockTimeoutException e) {
            return new DictionaryOperationResult.Failure<>("Dictionary is busy, please try again");
        } catch (RuntimeException e) {
            log.warn("Failed to update database for language '{}': {}", languageCode, e.getMessage());
            return new DictionaryOperationResult.Failure<>("Failed to save changes: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new DictionaryOperationResult.Failure<>("Operation interrupted");
        }

        return new DictionaryOperationResult.Success<>(updatedWord);
    }

    /**
     * Adds a new word to the dictionary for the given language and persists the change atomically.
     * A fresh UUID-based {@link WordId} is generated; the word is globally enabled by default.
     *
     * @param languageCode the BCP-47 language code identifying the dictionary
     * @param fromWord     source-language word (must not be blank)
     * @param toWord       target-language word (must not be blank)
     * @param example      optional usage example (may be empty)
     * @return {@link DictionaryOperationResult.Success} carrying the new {@link Word},
     *         or {@link DictionaryOperationResult.Failure} with a descriptive message on any error
     */
    public DictionaryOperationResult<Word> addWord(String languageCode, String fromWord, String toWord, String example) {
        var bundleOpt = dictionaryRepository.findLanguage(languageCode);
        if (bundleOpt.isEmpty()) {
            return new DictionaryOperationResult.Failure<>("Language not found: " + languageCode);
        }

        var newWordId = new WordId(UUID.randomUUID().toString());
        var newWord = new Word(newWordId, fromWord.trim(), toWord.trim(),
                example == null ? "" : example.trim(), true);

        try {
            dictionaryWriteLock.executeWithLock(languageCode, LOCK_TIMEOUT_MS, () -> {
                dictionaryRepository.insertWord(languageCode, newWordId.value(), newWord.fromWord(), newWord.toWord(), newWord.example(), true);
                auditLogService.logWordAdd(languageCode, newWordId, newWord);
                dataHealthService.reload();
            });
        } catch (DictionaryWriteLock.DictionaryLockTimeoutException e) {
            return new DictionaryOperationResult.Failure<>("Dictionary is busy, please try again");
        } catch (RuntimeException e) {
            log.warn("Failed to update database for language '{}': {}", languageCode, e.getMessage());
            return new DictionaryOperationResult.Failure<>("Failed to save changes: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new DictionaryOperationResult.Failure<>("Operation interrupted");
        }

        return new DictionaryOperationResult.Success<>(newWord);
    }
}
