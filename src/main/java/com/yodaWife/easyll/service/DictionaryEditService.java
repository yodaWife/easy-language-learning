package com.yodawife.easyll.service;

import com.yodawife.easyll.config.DictionaryProperties;
import com.yodawife.easyll.domain.DictionaryOperationResult;
import com.yodawife.easyll.domain.ModeEligibility;
import com.yodawife.easyll.domain.Word;
import com.yodawife.easyll.domain.WordId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles dictionary toggle operations (global and mode-scoped) with per-language write locking,
 * atomic CSV persistence, audit logging, and in-memory snapshot refresh.
 */
@Service
public class DictionaryEditService {

    private static final Logger log = LoggerFactory.getLogger(DictionaryEditService.class);
    private static final long LOCK_TIMEOUT_MS = 5_000L;
    private static final String CLASSPATH_PREFIX = "classpath:";
    private static final String WORDS_CSV = "words.csv";
    private static final String MODE_ELIGIBILITY_CSV = "mode-eligibility.csv";

    private final DataHealthService dataHealthService;
    private final DictionaryWriteLock dictionaryWriteLock;
    private final CsvPersistence csvPersistence;
    private final DictionaryAuditLogService auditLogService;
    private final DictionaryProperties dictionaryProperties;

    public DictionaryEditService(
            DataHealthService dataHealthService,
            DictionaryWriteLock dictionaryWriteLock,
            CsvPersistence csvPersistence,
            DictionaryAuditLogService auditLogService,
            DictionaryProperties dictionaryProperties) {
        this.dataHealthService = dataHealthService;
        this.dictionaryWriteLock = dictionaryWriteLock;
        this.csvPersistence = csvPersistence;
        this.auditLogService = auditLogService;
        this.dictionaryProperties = dictionaryProperties;
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
        if (!isEditableRootPath()) {
            return new DictionaryOperationResult.Failure<>(
                    "Dictionary editing requires a filesystem root path; current app.dictionaries.root-path is classpath-based");
        }

        var bundleOpt = dataHealthService.snapshot().getLanguageBundle(languageCode);
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

        var updatedWords = bundle.words().stream()
                .map(w -> w.wordId().equals(wordId) ? updatedWord : w)
                .toList();

        try {
            var wordsPath = resolveLanguagePath(languageCode, WORDS_CSV);
            dictionaryWriteLock.executeWithLock(languageCode, LOCK_TIMEOUT_MS, () -> {
                try {
                    csvPersistence.writeWords(wordsPath, updatedWords);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                auditLogService.logGlobalToggle(languageCode, wordId, word.globalEnabled(), newEnabled);
                dataHealthService.reload();
            });
        } catch (DictionaryWriteLock.DictionaryLockTimeoutException e) {
            return new DictionaryOperationResult.Failure<>("Dictionary is busy, please try again");
        } catch (UncheckedIOException e) {
            log.warn("Failed to write words CSV for language '{}': {}", languageCode, e.getMessage());
            var cause = e.getCause();
            return new DictionaryOperationResult.Failure<>("Failed to save changes: " +
                    String.valueOf(cause != null ? cause.getMessage() : e.getMessage()));
        } catch (IOException e) {
            log.warn("Failed to resolve path for language '{}': {}", languageCode, e.getMessage());
            return new DictionaryOperationResult.Failure<>("Failed to save changes: " + String.valueOf(e.getMessage()));
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
        if (!isEditableRootPath()) {
            return new DictionaryOperationResult.Failure<>(
                    "Dictionary editing requires a filesystem root path; current app.dictionaries.root-path is classpath-based");
        }

        var bundleOpt = dataHealthService.snapshot().getLanguageBundle(languageCode);
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

        var updatedEligibilities = buildUpdatedEligibilities(bundle.modeEligibilities(), wordId, mode, updatedEligibility);

        try {
            var eligibilityPath = resolveLanguagePath(languageCode, MODE_ELIGIBILITY_CSV);
            dictionaryWriteLock.executeWithLock(languageCode, LOCK_TIMEOUT_MS, () -> {
                try {
                    csvPersistence.writeModeEligibilities(eligibilityPath, updatedEligibilities);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                auditLogService.logModeToggle(languageCode, wordId, mode, existing.enabled(), newEnabled);
                dataHealthService.reload();
            });
        } catch (DictionaryWriteLock.DictionaryLockTimeoutException e) {
            return new DictionaryOperationResult.Failure<>("Dictionary is busy, please try again");
        } catch (UncheckedIOException e) {
            log.warn("Failed to write mode-eligibility CSV for language '{}': {}", languageCode, e.getMessage());
            var cause = e.getCause();
            return new DictionaryOperationResult.Failure<>("Failed to save changes: " +
                    String.valueOf(cause != null ? cause.getMessage() : e.getMessage()));
        } catch (IOException e) {
            log.warn("Failed to resolve path for language '{}': {}", languageCode, e.getMessage());
            return new DictionaryOperationResult.Failure<>("Failed to save changes: " + String.valueOf(e.getMessage()));
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
        if (!isEditableRootPath()) {
            return new DictionaryOperationResult.Failure<>(
                    "Dictionary editing requires a filesystem root path; current app.dictionaries.root-path is classpath-based");
        }

        var bundleOpt = dataHealthService.snapshot().getLanguageBundle(languageCode);
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

        var updatedWords = bundle.words().stream()
                .map(w -> w.wordId().equals(wordId) ? updatedWord : w)
                .toList();

        try {
            var wordsPath = resolveLanguagePath(languageCode, WORDS_CSV);
            dictionaryWriteLock.executeWithLock(languageCode, LOCK_TIMEOUT_MS, () -> {
                try {
                    csvPersistence.writeWords(wordsPath, updatedWords);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                auditLogService.logWordEdit(languageCode, wordId, word, updatedWord);
                dataHealthService.reload();
            });
        } catch (DictionaryWriteLock.DictionaryLockTimeoutException e) {
            return new DictionaryOperationResult.Failure<>("Dictionary is busy, please try again");
        } catch (UncheckedIOException e) {
            log.warn("Failed to write words CSV for language '{}': {}", languageCode, e.getMessage());
            var cause = e.getCause();
            return new DictionaryOperationResult.Failure<>("Failed to save changes: " +
                    String.valueOf(cause != null ? cause.getMessage() : e.getMessage()));
        } catch (IOException e) {
            log.warn("Failed to resolve path for language '{}': {}", languageCode, e.getMessage());
            return new DictionaryOperationResult.Failure<>("Failed to save changes: " + String.valueOf(e.getMessage()));
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
        if (!isEditableRootPath()) {
            return new DictionaryOperationResult.Failure<>(
                    "Dictionary editing requires a filesystem root path; current app.dictionaries.root-path is classpath-based");
        }

        var bundleOpt = dataHealthService.snapshot().getLanguageBundle(languageCode);
        if (bundleOpt.isEmpty()) {
            return new DictionaryOperationResult.Failure<>("Language not found: " + languageCode);
        }

        var bundle = bundleOpt.get();
        var newWordId = new WordId(UUID.randomUUID().toString());
        var newWord = new Word(newWordId, fromWord.trim(), toWord.trim(),
                example == null ? "" : example.trim(), true);

        var updatedWords = new ArrayList<>(bundle.words());
        updatedWords.add(newWord);

        try {
            var wordsPath = resolveLanguagePath(languageCode, WORDS_CSV);
            dictionaryWriteLock.executeWithLock(languageCode, LOCK_TIMEOUT_MS, () -> {
                try {
                    csvPersistence.writeWords(wordsPath, updatedWords);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                auditLogService.logWordAdd(languageCode, newWordId, newWord);
                dataHealthService.reload();
            });
        } catch (DictionaryWriteLock.DictionaryLockTimeoutException e) {
            return new DictionaryOperationResult.Failure<>("Dictionary is busy, please try again");
        } catch (UncheckedIOException e) {
            log.warn("Failed to write words CSV for language '{}': {}", languageCode, e.getMessage());
            var cause = e.getCause();
            return new DictionaryOperationResult.Failure<>("Failed to save changes: " +
                    String.valueOf(cause != null ? cause.getMessage() : e.getMessage()));
        } catch (IOException e) {
            log.warn("Failed to resolve path for language '{}': {}", languageCode, e.getMessage());
            return new DictionaryOperationResult.Failure<>("Failed to save changes: " + String.valueOf(e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new DictionaryOperationResult.Failure<>("Operation interrupted");
        }

        return new DictionaryOperationResult.Success<>(newWord);
    }

    /**
     * Resolves the absolute {@link Path} to a file within the given language's dictionary directory.
     *
     * <p>Supports both {@code classpath:}-prefixed paths and plain filesystem paths, mirroring
     * the resolution logic used by {@code DictionaryDiscoveryService}.
     *
     * @param languageCode the BCP-47 language code (subdirectory name)
     * @param filename     the filename to resolve within the language directory
     * @return resolved path to {@code rootPath/languageCode/filename}
     * @throws IOException if a {@code classpath:} URL cannot be converted to a URI
     */
    private Path resolveLanguagePath(String languageCode, String filename) throws IOException {
        var rawRootPath = dictionaryProperties.getRootPath();
        Path root;
        if (rawRootPath.startsWith(CLASSPATH_PREFIX)) {
            var classpathRelative = rawRootPath.substring(CLASSPATH_PREFIX.length());
            var resource = new ClassPathResource(classpathRelative);
            try {
                root = Path.of(resource.getURL().toURI());
            } catch (URISyntaxException e) {
                throw new IOException("Failed to convert classpath URL to URI: " + e.getMessage(), e);
            }
        } else {
            root = Path.of(rawRootPath);
        }
        return root.resolve(languageCode).resolve(filename);
    }

    private boolean isEditableRootPath() {
        var rootPath = dictionaryProperties.getRootPath();
        return !rootPath.startsWith(CLASSPATH_PREFIX);
    }

    private List<ModeEligibility> buildUpdatedEligibilities(
            List<ModeEligibility> existing,
            WordId wordId,
            String mode,
            ModeEligibility updated) {
        var result = new ArrayList<ModeEligibility>(existing.size());
        for (var me : existing) {
            if (!(me.wordId().equals(wordId) && me.mode().equals(mode))) {
                result.add(me);
            }
        }
        result.add(updated);
        return result;
    }
}
